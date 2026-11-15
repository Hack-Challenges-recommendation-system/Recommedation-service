package team.nofantasy.recommendationsystem.catalogservice.dto.controller.material;

import java.util.List;

/**
 * A material as clients see it. For a paid material {@code summary} is a short benefit; {@code url} is the provided link.
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
        Boolean paid,
        String url
) {
}
