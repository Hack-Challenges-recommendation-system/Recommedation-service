package team.nofantasy.recommendationsystem.catalogservice.dto.service.material;

import java.util.List;

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
        Boolean paid,
        String url
) {
}
