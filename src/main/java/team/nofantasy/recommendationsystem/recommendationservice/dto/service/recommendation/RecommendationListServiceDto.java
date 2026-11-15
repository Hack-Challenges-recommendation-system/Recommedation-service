package team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation;

import java.util.List;
import java.util.UUID;

/**
 * A list of recommendations.
 *
 * @param recommendationId new for every response; the frontend sends it back with the events of the list.
 * @param variant the A/B variant of the user: {@code llm} or {@code baseline}.
 * @param engine what built the list: the LLM, or the topic-match baseline when the variant is {@code baseline} or the
 *               LLM can't answer.
 * @param clarifyingQuestion a question to the user when the goal is missing or too vague, or {@code null}.
 * @param orderExplanation why the list goes in this order: what to start with and what comes next; {@code null} for an
 *                         empty list.
 */
public record RecommendationListServiceDto(
        UUID recommendationId,
        String variant,
        Engine engine,
        String clarifyingQuestion,
        String orderExplanation,
        List<RecommendationItemServiceDto> items
) {
    public enum Engine {
        LLM,
        BASELINE
    }
}
