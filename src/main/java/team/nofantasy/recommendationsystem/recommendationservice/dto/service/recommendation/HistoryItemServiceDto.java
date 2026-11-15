package team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation;

/**
 * An event of the user's own history as the LLM sees it: what happened to which material, nothing about the user.
 *
 * @param event {@code START}, {@code COMPLETE}, {@code LIKE} or {@code DISLIKE}.
 * @param title {@code null} if the material is no longer in the catalog.
 */
public record HistoryItemServiceDto(
        String event,
        String materialId,
        String title
) {
}
