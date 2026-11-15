package team.nofantasy.recommendationsystem.catalogservice.dto.service.importer;

import java.util.List;

/**
 * One material in the import file: a JSON array of these objects.
 * The case data has its own layout; convert it to this format, or add an adapter in the importer package.
 */
public record MaterialImportDto(
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
