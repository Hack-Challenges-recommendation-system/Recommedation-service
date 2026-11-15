package team.nofantasy.recommendationsystem.recommendationservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialServiceDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole request path, with WireMock standing in for profile-, catalog- and metric-service and for the Anthropic
 * API: what reaches the LLM, how its answer is checked, the cache, and the baseline when the LLM fails.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationIntegrationTest {
    private static final WireMockServer wireMock = new WireMockServer(options().dynamicPort());
    private static final String NICKNAME = "founder-nick";
    private static final String EMAIL = "ivan@example.com";

    static {
        wireMock.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("app.services.profile-url", wireMock::baseUrl);
        registry.add("app.services.catalog-url", wireMock::baseUrl);
        registry.add("app.services.metric-url", wireMock::baseUrl);
        registry.add("spring.ai.anthropic.base-url", wireMock::baseUrl);
        registry.add("spring.ai.anthropic.api-key", () -> "test-key");
        registry.add("spring.ai.anthropic.max-retries", () -> "0");
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void stubServices() {
        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/materials")).willReturn(okJson("""
                {"items": %s, "page": 0, "size": 500, "totalElements": 8, "totalPages": 1}
                """.formatted(TestFixtures.materialsJson()))));
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/materials/search")).willReturn(okJson(searchResults(
                Map.of("m-001", 0.6, "m-008", 0.4, "m-005", 0.2, "m-002", 0.1)
        ))));
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/metrics/materials/popular"))
                .willReturn(okJson("[{\"materialId\": \"m-005\", \"users\": 12}]")));
        wireMock.stubFor(get(urlPathMatching("/api/v1/metrics/materials/[^/]+/next")).willReturn(okJson("[]")));
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/metrics/materials/m-003/next")).atPriority(1)
                .willReturn(okJson("[{\"materialId\": \"m-008\", \"users\": 3}]")));
    }

    private static String searchResults(Map<String, Double> scores) {
        List<Map<String, Object>> results = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(score -> {
                    MaterialServiceDto material = TestFixtures.material(score.getKey());
                    return Map.<String, Object>of("material", material, "score", score.getValue());
                })
                .toList();
        return TestFixtures.JSON.writeValueAsString(results);
    }

    /**
     * profile-service answers with the nickname and user id too; neither may reach the LLM.
     */
    private static void stubUser(UUID userId) {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/profile/me")).withHeader("X-User-Id", equalTo(userId.toString()))
                .willReturn(okJson("""
                        {"userId": "%s", "nickname": "%s", "businessStage": "LAUNCH",
                         "businessSphere": "Розничная торговля", "experience": "NONE",
                         "goal": "Найти первых клиентов для магазина, пишите на %s",
                         "preferredFormats": ["VIDEO"], "onboardingCompleted": true,
                         "updatedAt": "2026-11-15T10:00:00Z"}
                        """.formatted(userId, NICKNAME, EMAIL))));
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/events/me")).withHeader("X-User-Id", equalTo(userId.toString()))
                .willReturn(okJson("""
                        [{"type": "IMPRESSION", "materialId": "m-004", "occurredAt": "2026-11-15T10:05:00Z"},
                         {"type": "START", "materialId": "m-003", "occurredAt": "2026-11-15T10:04:00Z"},
                         {"type": "COMPLETE", "materialId": "m-002", "occurredAt": "2026-11-15T10:03:00Z"}]
                        """)));
    }

    /**
     * A Messages API response the way Claude answers: a thinking block, then the JSON text.
     */
    private static void stubLlmAnswer(String answer) {
        wireMock.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(okJson("""
                {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-sonnet-5-5",
                 "content": [{"type": "thinking", "thinking": "", "signature": "test-signature"},
                             {"type": "text", "text": %s}],
                 "stop_reason": "end_turn", "stop_sequence": null,
                 "usage": {"input_tokens": 1500, "output_tokens": 300}}
                """.formatted(TestFixtures.JSON.writeValueAsString(answer)))));
    }

    private ResultActions requestRecommendations(UUID userId) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/recommendations/me")
                .servletPath("/api/v1")
                .header("X-User-Id", userId));
    }

    private static List<LoggedRequest> llmRequests() {
        return wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/messages")));
    }

    @Test
    void llmChoosesFromCandidatesAndGetsNoPersonalData() throws Exception {
        UUID userId = UUID.randomUUID();
        stubUser(userId);
        stubLlmAnswer("""
                {"items": [
                  {"id": "m-005", "reason": "Бесплатное продвижение подойдёт для первых покупателей магазина",
                   "nextStep": "Опубликуйте первый пост о магазине"},
                  {"id": "m-999", "reason": "Выдуманный материал", "nextStep": "-"},
                  {"id": "m-001", "reason": "Пошаговый план поиска первых клиентов", "nextStep": "Составьте список из 10 знакомых"},
                  {"id": "m-001", "reason": "Повтор", "nextStep": "Повтор"},
                  {"id": "m-002", "reason": "Уже пройден", "nextStep": "-"}
                 ],
                 "clarifyingQuestion": ""}
                """);

        requestRecommendations(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("llm"))
                .andExpect(jsonPath("$.variant").value("llm"))
                .andExpect(jsonPath("$.clarifyingQuestion").isEmpty())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].material.id").value("m-005"))
                .andExpect(jsonPath("$.items[0].material.url").value("https://example.com/materials/m-005"))
                .andExpect(jsonPath("$.items[0].reason").value("Бесплатное продвижение подойдёт для первых покупателей магазина"))
                .andExpect(jsonPath("$.items[0].nextStep").value("Опубликуйте первый пост о магазине"))
                .andExpect(jsonPath("$.items[1].material.id").value("m-001"))
                .andExpect(jsonPath("$.items[1].position").value(1));

        assertThat(llmRequests()).hasSize(1);
        String body = llmRequests().getFirst().getBodyAsString();
        Map<String, Object> request = JsonPath.read(body, "$");
        assertThat(request).containsEntry("model", "claude-sonnet-5-5")
                .containsEntry("max_tokens", 8000)
                .doesNotContainKeys("temperature", "top_p", "top_k", "thinking");
        assertThat((String) JsonPath.read(body, "$.output_config.effort")).isEqualTo("medium");
        assertThat((String) JsonPath.read(body, "$.output_config.format.type")).isEqualTo("json_schema");
        assertThat(body).doesNotContain(userId.toString(), NICKNAME, EMAIL).contains("[email]");

        Object content = JsonPath.read(body, "$.messages[0].content");
        String userMessage = content instanceof String text ? text : JsonPath.read(body, "$.messages[0].content[0].text");
        List<String> candidates = JsonPath.read(userMessage, "$.candidates[*].id");
        assertThat(candidates).as("completed m-002 is not a candidate")
                .containsExactly("m-001", "m-008", "m-005", "m-003", "m-004", "m-006", "m-007");
        List<String> history = JsonPath.read(userMessage, "$.history[*].materialId");
        assertThat(history).as("impressions are not history").containsExactly("m-003", "m-002");
    }

    @Test
    void sameProfileAndHistoryDoNotCallTheLlmAgain() throws Exception {
        UUID userId = UUID.randomUUID();
        stubUser(userId);
        stubLlmAnswer("""
                {"items": [{"id": "m-001", "reason": "Пошаговый план", "nextStep": "Составьте список клиентов"}],
                 "clarifyingQuestion": ""}
                """);

        String first = requestRecommendations(userId).andReturn().getResponse().getContentAsString();
        String second = requestRecommendations(userId).andReturn().getResponse().getContentAsString();

        assertThat(llmRequests()).hasSize(1);
        assertThat((String) JsonPath.read(second, "$.items[0].material.id")).isEqualTo("m-001");
        assertThat((String) JsonPath.read(second, "$.recommendationId"))
                .isNotEqualTo(JsonPath.read(first, "$.recommendationId"));
    }

    @Test
    void failedLlmGivesTheTopicMatchBaseline() throws Exception {
        UUID userId = UUID.randomUUID();
        stubUser(userId);
        wireMock.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(serverError()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\": \"error\", \"error\": {\"type\": \"api_error\", \"message\": \"Internal error\"}}")));

        requestRecommendations(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("baseline"))
                .andExpect(jsonPath("$.variant").value("llm"))
                .andExpect(jsonPath("$.items.length()").value(6))
                .andExpect(jsonPath("$.items[0].material.id").value("m-001"))
                .andExpect(jsonPath("$.items[0].reason").value("Совпадает с вашей задачей по темам: маркетинг, продажи"))
                .andExpect(jsonPath("$.items[1].material.id").value("m-008"))
                .andExpect(jsonPath("$.items[2].material.id").value("m-005"));
    }
}
