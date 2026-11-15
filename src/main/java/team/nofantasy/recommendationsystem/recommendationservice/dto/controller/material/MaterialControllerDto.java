package team.nofantasy.recommendationsystem.recommendationservice.dto.controller.material;

import java.util.List;

/**
 * A material as catalog-service gives it. For a paid material {@code summary} is a short benefit; {@code url} is the
 * provided link.
 */
public record MaterialControllerDto(
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
}
