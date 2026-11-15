package team.nofantasy.recommendationsystem.recommendationservice.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How lists of recommendations are built.
 *
 * @param items materials in one list.
 * @param maxCandidates materials the LLM chooses from.
 * @param historyLimit newest events of the user that are read.
 * @param cacheTtl how long an LLM list is reused while the profile and the history stay the same.
 * @param fallbackCacheTtl how long a baseline list is reused, so a failing LLM is retried soon but not on every request.
 * @param catalogTtl how long the catalog snapshot is reused.
 * @param llmEffort effort of the model: {@code low}, {@code medium}, {@code high}, {@code xhigh} or {@code max}.
 * @param llmShare percent of users in the {@code llm} variant of the A/B test; the others get the baseline.
 */
@ConfigurationProperties("app.recommendations")
public record RecommendationProperties(
        int items,
        int maxCandidates,
        int historyLimit,
        Duration cacheTtl,
        Duration fallbackCacheTtl,
        Duration catalogTtl,
        String llmEffort,
        int llmShare
) {
}
