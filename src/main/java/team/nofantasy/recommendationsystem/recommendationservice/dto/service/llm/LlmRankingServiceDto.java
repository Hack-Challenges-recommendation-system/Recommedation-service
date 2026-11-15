package team.nofantasy.recommendationsystem.recommendationservice.dto.service.llm;

import java.util.List;
import java.util.Objects;

/**
 * The answer of the LLM, in the shape of {@code LlmRankingService.ANSWER_SCHEMA}. Not trusted: ids are checked against
 * the candidates before anything is shown.
 *
 * @param items chosen candidates, best first.
 * @param clarifyingQuestion empty when the model has no question.
 */
public record LlmRankingServiceDto(
        List<Pick> items,
        String clarifyingQuestion
) {
    public LlmRankingServiceDto {
        items = items == null ? List.of() : items.stream().filter(Objects::nonNull).toList();
    }

    public record Pick(
            String id,
            String reason,
            String nextStep
    ) {
    }
}
