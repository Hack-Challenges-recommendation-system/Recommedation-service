package team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog;

import java.util.List;
import java.util.Objects;

/**
 * A material from catalog-service. For a paid material {@code summary} is only its short benefit and {@code url} is
 * the provided link.
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
        String url
) {
    public MaterialServiceDto {
        topics = withoutNulls(topics);
        businessStages = withoutNulls(businessStages);
        spheres = withoutNulls(spheres);
    }

    private static List<String> withoutNulls(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }
}
