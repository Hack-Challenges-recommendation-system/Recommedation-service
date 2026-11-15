package team.nofantasy.recommendationsystem.recommendationservice.service;

import org.junit.jupiter.api.Test;
import team.nofantasy.recommendationsystem.recommendationservice.TestFixtures;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentServiceTest {
    @Test
    void everyUserGetsTheLlmByDefault() {
        ExperimentService experimentService = new ExperimentService(TestFixtures.properties(100));

        assertThat(IntStream.range(0, 500).mapToObj(i -> experimentService.getVariant(UUID.randomUUID())))
                .containsOnly(ExperimentService.LLM_VARIANT);
    }

    @Test
    void zeroShareKeepsEveryoneOnTheBaseline() {
        ExperimentService experimentService = new ExperimentService(TestFixtures.properties(0));

        assertThat(IntStream.range(0, 500).mapToObj(i -> experimentService.getVariant(UUID.randomUUID())))
                .containsOnly(ExperimentService.BASELINE_VARIANT);
    }

    @Test
    void variantIsStableAndFollowsTheShare() {
        ExperimentService experimentService = new ExperimentService(TestFixtures.properties(50));
        int users = 4000;
        int inLlm = 0;

        for (int i = 0; i < users; i++) {
            UUID userId = UUID.randomUUID();
            String variant = experimentService.getVariant(userId);
            assertThat(experimentService.getVariant(userId)).isEqualTo(variant);
            if (variant.equals(ExperimentService.LLM_VARIANT)) {
                inLlm++;
            }
        }

        assertThat((double) inLlm / users).isBetween(0.45, 0.55);
    }
}
