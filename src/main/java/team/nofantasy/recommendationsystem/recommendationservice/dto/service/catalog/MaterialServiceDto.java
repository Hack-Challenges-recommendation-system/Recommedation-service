package team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog;

import java.util.List;
import java.util.Objects;

/**
 * A material from catalog-service. For a paid material {@code summary} is only its short benefit and {@code url} is
 * the provided link.
 *
 * @param paid true for every material that isn't free.
 * @param access {@code FREE}, {@code SUBSCRIPTION} (part of a subscription) or {@code PURCHASE} (bought separately);
 *               derived from {@code paid} when catalog-service doesn't send it.
 */
public record MaterialServiceDto(
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
        String url
) {
    public static final String FREE = "FREE";
    public static final String PURCHASE = "PURCHASE";

    public MaterialServiceDto {
        topics = withoutNulls(topics);
        businessStages = withoutNulls(businessStages);
        spheres = withoutNulls(spheres);
        if (access == null || access.isBlank()) {
            access = paid ? PURCHASE : FREE;
        }
    }

    private static List<String> withoutNulls(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }
}
