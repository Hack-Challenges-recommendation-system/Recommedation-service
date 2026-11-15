package team.nofantasy.recommendationsystem.recommendationservice.service;

import com.anthropic.models.messages.OutputConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import team.nofantasy.recommendationsystem.recommendationservice.TestFixtures;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.llm.LlmRankingServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.profile.ProfileServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.CandidateServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.HistoryItemServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.exception.LlmRankingException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LlmRankingServiceTest {
    private static final ProfileServiceDto PROFILE = new ProfileServiceDto(
            "LAUNCH",
            "Розничная торговля",
            "NONE",
            "Найти первых клиентов, пишите ivan@example.com или +7 999 123-45-67",
            List.of("VIDEO"),
            Instant.EPOCH
    );
    private static final String ANSWER = """
            {"items": [{"id": "m-005", "reason": "Бесплатное продвижение для первых покупателей",
                        "nextStep": "Опубликуйте первый пост о магазине"}],
             "orderExplanation": "Начните с бесплатного продвижения",
             "clarifyingQuestion": ""}
            """;

    private final ChatModel chatModel = mock(ChatModel.class);
    private final List<CandidateServiceDto> candidates = List.of(
            new CandidateServiceDto(
                    TestFixtures.material("m-005"),
                    0.23456,
                    12,
                    "Воронка продаж для малого бизнеса",
                    new CandidateServiceDto.HistorySource("Как найти первых клиентов", "LIKE"),
                    true,
                    true
            ),
            TestFixtures.candidate("m-008", 0)
    );

    private LlmRankingService service(String apiKey) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("chatModel", chatModel));
        return new LlmRankingService(
                beans.getBeanProvider(ChatModel.class),
                TestFixtures.JSON,
                TestFixtures.properties(100),
                mock(RecommendationTelemetry.class),
                apiKey,
                "claude-sonnet-5-5",
                8000
        );
    }

    private static ChatResponse response(String... texts) {
        return new ChatResponse(List.of(texts).stream().map(text -> new Generation(new AssistantMessage(text))).toList());
    }

    @Test
    void requestHoldsOnlyBusinessFieldsAndHidesContacts() {
        String request = TestFixtures.JSON.writeValueAsString(service("key").toRequest(
                PROFILE, List.of(new HistoryItemServiceDto("COMPLETE", "m-002", "Налоги для ИП: какой режим выбрать")), candidates, 6
        ));

        assertThat(request)
                .doesNotContain("ivan@example.com", "999 123")
                .contains("[email]", "[phone]", "\"businessStage\":\"LAUNCH\"", "\"items\":6")
                .doesNotContain("\"id\":\"m-005\"")
                .contains("\"id\":\"c1\"", "\"topicMatch\":0.235", "\"popularUsers\":12", "\"takenAfter\":\"Воронка продаж для малого бизнеса\"")
                .contains("\"similarTo\":\"Как найти первых клиентов\"")
                .contains("\"title\":\"Налоги для ИП: какой режим выбрать\"");
        var requestCandidates = TestFixtures.JSON.readTree(request).get("candidates");
        // m-008 has no signal, so it has no signals object
        assertThat(requestCandidates.get(1).has("signals")).isFalse();
        assertThat(requestCandidates.get(1).get("access").asString()).as("m-008 is paid").isEqualTo("PURCHASE");
        assertThat(requestCandidates.get(0).has("access")).as("a free material has no access").isFalse();
    }

    @Test
    void callsTheModelWithEffortAndSchemaAndReadsTheAnswerAfterThinking() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("", ANSWER));

        LlmRankingServiceDto ranking = service("key").rank(PROFILE, List.of(), candidates, 6);

        assertThat(ranking.items()).extracting(LlmRankingServiceDto.Pick::id).containsExactly("m-005");
        assertThat(ranking.orderExplanation()).isEqualTo("Начните с бесплатного продвижения");
        assertThat(ranking.clarifyingQuestion()).isEmpty();

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        AnthropicChatOptions options = (AnthropicChatOptions) prompt.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("claude-sonnet-5-5");
        assertThat(options.getMaxTokens()).isEqualTo(8000);
        assertThat(options.getOutputConfig().effort()).contains(OutputConfig.Effort.MEDIUM);
        assertThat(options.getOutputSchema()).contains("\"clarifyingQuestion\"", "\"orderExplanation\"");
        assertThat(options.getTemperature()).as("current models reject temperature").isNull();
        assertThat(options.getTopP()).isNull();
        assertThat(options.getTopK()).isNull();
        assertThat(options.getThinking()).as("thinking stays adaptive").isNull();
    }

    @Test
    void shortIdsOfTheAnswerBecomeMaterialIds() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("""
                {"items": [{"id": " c2 ", "reason": "Переговоры", "nextStep": "Позвоните"},
                           {"id": "c1", "reason": "Продвижение", "nextStep": "Опубликуйте пост"},
                           {"id": "c9", "reason": "Нет такого", "nextStep": "-"}],
                 "orderExplanation": "", "clarifyingQuestion": ""}
                """));

        LlmRankingServiceDto ranking = service("key").rank(PROFILE, List.of(), candidates, 6);

        // c9 isn't a candidate: it stays as it is, and validation leaves it out
        assertThat(ranking.items()).extracting(LlmRankingServiceDto.Pick::id).containsExactly("m-008", "m-005", "c9");
    }

    @Test
    void shortIdsNeverReachTheTexts() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("""
                {"items": [{"id": "c1", "reason": "После c2 закрепит результат", "nextStep": "Откройте курс (c1)"}],
                 "orderExplanation": "Сначала основа (курс c1), затем переговоры (c2) и c9.",
                 "clarifyingQuestion": ""}
                """));

        LlmRankingServiceDto ranking = service("key").rank(PROFILE, List.of(), candidates, 6);

        assertThat(ranking.orderExplanation()).isEqualTo("Сначала основа, затем переговоры и c9.");
        assertThat(ranking.items().getFirst().reason()).isEqualTo("После «Переговоры с первыми клиентами» закрепит результат");
        assertThat(ranking.items().getFirst().nextStep()).isEqualTo("Откройте курс");
    }

    @Test
    void toleratesACodeFence() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("```json\n" + ANSWER + "\n```"));

        assertThat(service("key").rank(PROFILE, List.of(), candidates, 6).items()).hasSize(1);
    }

    @Test
    void answerThatIsNotJsonIsAnError() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("Вот подборка: m-005, m-008"));

        assertThatThrownBy(() -> service("key").rank(PROFILE, List.of(), candidates, 6))
                .isInstanceOf(LlmRankingException.class);
    }

    @Test
    void emptyAnswerIsAnError() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response(""));

        assertThatThrownBy(() -> service("key").rank(PROFILE, List.of(), candidates, 6))
                .isInstanceOf(LlmRankingException.class)
                .hasMessageContaining("no text");
    }

    @Test
    void failedCallIsAnError() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("connection reset"));

        assertThatThrownBy(() -> service("key").rank(PROFILE, List.of(), candidates, 6))
                .isInstanceOf(LlmRankingException.class)
                .hasMessageContaining("connection reset");
    }

    @Test
    void withoutKeyTheModelIsNeverCalled() {
        LlmRankingService service = service("");

        assertThat(service.isAvailable()).isFalse();
        assertThatThrownBy(() -> service.rank(PROFILE, List.of(), candidates, 6)).isInstanceOf(LlmRankingException.class);
        verifyNoInteractions(chatModel);
    }
}
