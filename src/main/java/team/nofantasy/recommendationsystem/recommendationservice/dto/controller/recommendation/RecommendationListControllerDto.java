package team.nofantasy.recommendationsystem.recommendationservice.dto.controller.recommendation;

import java.util.List;
import java.util.UUID;

/**
 * The list the frontend shows, in the shape its {@code RecommendationList} expects.
 *
 * @param variant A/B variant: {@code llm} or {@code baseline}.
 * @param engine {@code llm}, or {@code baseline} when the list comes from topic matching.
 * @param clarifyingQuestion a question to the user when the goal is missing or too vague, or {@code null}.
 * @param orderExplanation why the list goes in this order, or {@code null} for an empty list.
 */
public record RecommendationListControllerDto(
        UUID recommendationId,
        String variant,
        String engine,
        String clarifyingQuestion,
        String orderExplanation,
        List<RecommendationItemControllerDto> items
) {
}
