package team.nofantasy.recommendationsystem.recommendationservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import team.nofantasy.recommendationsystem.recommendationservice.TestFixtures;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.RecommendationItemServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.RecommendationListServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.RecommendationListServiceDto.Engine;
import team.nofantasy.recommendationsystem.recommendationservice.exception.CatalogUnavailableException;
import team.nofantasy.recommendationsystem.recommendationservice.mapper.RecommendationMapperImpl;
import team.nofantasy.recommendationsystem.recommendationservice.service.RecommendationService;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
@Import(RecommendationMapperImpl.class)
@ActiveProfiles("test")
class RecommendationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    @Test
    void answersInTheShapeTheFrontendExpects() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID recommendationId = UUID.randomUUID();
        when(recommendationService.getRecommendations(userId)).thenReturn(new RecommendationListServiceDto(
                recommendationId,
                "llm",
                Engine.LLM,
                null,
                List.of(new RecommendationItemServiceDto(
                        TestFixtures.material("m-008"), "Скрипты помогут на первых звонках", "Позвоните трём клиентам", 0
                ))
        ));

        mockMvc.perform(api(get("/api/v1/recommendations/me")).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationId").value(recommendationId.toString()))
                .andExpect(jsonPath("$.variant").value("llm"))
                .andExpect(jsonPath("$.engine").value("llm"))
                .andExpect(jsonPath("$.clarifyingQuestion").isEmpty())
                .andExpect(jsonPath("$.items[0].material.id").value("m-008"))
                .andExpect(jsonPath("$.items[0].material.paid").value(true))
                .andExpect(jsonPath("$.items[0].material.url").value("https://partner.example.com/negotiations"))
                .andExpect(jsonPath("$.items[0].material.topics[0]").value("продажи"))
                .andExpect(jsonPath("$.items[0].reason").value("Скрипты помогут на первых звонках"))
                .andExpect(jsonPath("$.items[0].nextStep").value("Позвоните трём клиентам"))
                .andExpect(jsonPath("$.items[0].position").value(0));
    }

    @Test
    void requiresUserIdFromGateway() throws Exception {
        mockMvc.perform(api(get("/api/v1/recommendations/me")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recommendationService);
    }

    @Test
    void catalogOutageIsServiceUnavailable() throws Exception {
        UUID userId = UUID.randomUUID();
        when(recommendationService.getRecommendations(userId))
                .thenThrow(new CatalogUnavailableException("Catalog is unavailable", null));

        mockMvc.perform(api(get("/api/v1/recommendations/me")).header("X-User-Id", userId))
                .andExpect(status().isServiceUnavailable());
    }
}
