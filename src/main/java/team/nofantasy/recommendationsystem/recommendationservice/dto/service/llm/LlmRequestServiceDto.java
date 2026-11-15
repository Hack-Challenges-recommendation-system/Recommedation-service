package team.nofantasy.recommendationsystem.recommendationservice.dto.service.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.HistoryItemServiceDto;

import java.util.List;

/**
 * The user message to the LLM, sent as JSON. It holds only the business fields of the profile, the user's own history
 * and public fields of the candidates: never a user id, a name or contact details.
 *
 * @param items the most materials to return.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LlmRequestServiceDto(
        int items,
        Profile profile,
        List<HistoryItemServiceDto> history,
        List<Candidate> candidates
) {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Profile(
            String businessStage,
            String businessSphere,
            String experience,
            String goal,
            List<String> preferredFormats
    ) {
    }

    /**
     * @param access {@code SUBSCRIPTION} or {@code PURCHASE}; left out for a free material.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Candidate(
            String id,
            String title,
            String summary,
            String format,
            Integer durationMinutes,
            String level,
            List<String> topics,
            List<String> businessStages,
            List<String> spheres,
            boolean paid,
            String access,
            Signals signals
    ) {
    }

    /**
     * Why a material is a candidate; a signal that doesn't apply is left out.
     *
     * @param topicMatch full-text match with the goal and sphere.
     * @param popularUsers entrepreneurs who started or completed it.
     * @param takenAfter a material from the history that people often take before this one.
     * @param similarTo a material from the history that shares topics with this one.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Signals(
            Double topicMatch,
            Long popularUsers,
            String takenAfter,
            String similarTo,
            Boolean stageMatch,
            Boolean preferredFormat
    ) {
    }
}
