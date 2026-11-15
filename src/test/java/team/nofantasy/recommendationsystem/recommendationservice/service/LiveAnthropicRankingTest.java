package team.nofantasy.recommendationsystem.recommendationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import team.nofantasy.recommendationsystem.recommendationservice.TestFixtures;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.llm.LlmRankingServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.profile.ProfileServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.CandidateServiceDto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * One real call to the Anthropic API with the synthetic catalog: checks the model, the options and the answer schema
 * end to end, and saves the answer to {@code target/live-llm-answer.json} to judge its quality. It costs money, so it
 * runs only on request:
 * {@code LIVE_LLM_TEST=true mvn -B -ntp test -Dtest=LiveAnthropicRankingTest}, with {@code ANTHROPIC_API_KEY} in the
 * environment or in {@code .env}.
 */
@SpringBootTest(properties = "spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:}")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "LIVE_LLM_TEST", matches = "true")
@Slf4j
class LiveAnthropicRankingTest {
    @Autowired
    private LlmRankingService llmRankingService;

    @Test
    void ranksTheSyntheticCatalogForAFounderOfAShop() throws IOException {
        assumeTrue(llmRankingService.isAvailable(), "Set ANTHROPIC_API_KEY to run this test");
        ProfileServiceDto profile = new ProfileServiceDto(
                "LAUNCH",
                "Розничная торговля",
                "NONE",
                "Открываю небольшой магазин одежды и хочу найти первых покупателей",
                List.of("VIDEO"),
                Instant.now()
        );
        List<CandidateServiceDto> candidates = TestFixtures.materials().stream()
                .map(material -> new CandidateServiceDto(
                        material,
                        0,
                        0,
                        null,
                        null,
                        CandidateService.isStageMatch(material, profile),
                        CandidateService.isFormatMatch(material, profile)
                ))
                .toList();
        Set<String> candidateIds = candidates.stream().map(candidate -> candidate.material().id()).collect(Collectors.toSet());

        long startedAt = System.nanoTime();
        LlmRankingServiceDto ranking = llmRankingService.rank(profile, List.of(), candidates, 4);
        long latencyMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        // Kept for comparing answers with the baseline across runs
        Path answer = Path.of("target", "live-llm-answer.json");
        Files.writeString(answer, TestFixtures.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                Map.of("latencyMs", latencyMs, "profile", profile, "answer", ranking)
        ), StandardCharsets.UTF_8);
        log.info("Live answer in {} after {} ms", answer.toAbsolutePath(), latencyMs);

        assertThat(ranking.items()).isNotEmpty().allSatisfy(pick -> {
            assertThat(candidateIds).contains(pick.id());
            assertThat(pick.reason()).isNotBlank();
            assertThat(pick.nextStep()).isNotBlank();
        });
    }
}
