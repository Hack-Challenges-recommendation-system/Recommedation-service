package team.nofantasy.recommendationsystem.recommendationservice.service;

import org.springframework.stereotype.Service;
import team.nofantasy.recommendationsystem.recommendationservice.configuration.properties.RecommendationProperties;

import java.util.UUID;

/**
 * Assigns users to the A/B test of LLM recommendations against the topic-match baseline. The frontend sends the
 * variant back with every event of a list, and metric-service reports the metrics per variant.
 */
@Service
public class ExperimentService {
    public static final String LLM_VARIANT = "llm";
    public static final String BASELINE_VARIANT = "baseline";
    private static final String EXPERIMENT = "llm-vs-baseline";

    private final int llmShare;

    public ExperimentService(RecommendationProperties properties) {
        this.llmShare = properties.llmShare();
    }

    /**
     * The variant of the user. The same user always gets the same variant, and {@code llm-share} percent of users get
     * {@code llm}.
     */
    public String getVariant(UUID userId) {
        int bucket = Math.floorMod((EXPERIMENT + ":" + userId).hashCode(), 100);
        return bucket < llmShare ? LLM_VARIANT : BASELINE_VARIANT;
    }
}
