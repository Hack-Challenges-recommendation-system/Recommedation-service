package team.nofantasy.recommendationsystem.recommendationservice.dto.service.metric;

import java.time.Instant;

/**
 * One event of the user's history from metric-service, newest first.
 *
 * @param type {@code IMPRESSION}, {@code CLICK}, {@code START}, {@code COMPLETE}, {@code LIKE} or {@code DISLIKE}; a
 *             string, so a new type in metric-service doesn't break reading the history.
 */
public record EventServiceDto(
        String type,
        String materialId,
        Instant occurredAt
) {
}
