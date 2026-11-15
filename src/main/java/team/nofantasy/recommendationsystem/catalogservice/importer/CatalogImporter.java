package team.nofantasy.recommendationsystem.catalogservice.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import team.nofantasy.recommendationsystem.catalogservice.configuration.properties.CatalogProperties;
import team.nofantasy.recommendationsystem.catalogservice.dto.service.importer.MaterialImportDto;
import team.nofantasy.recommendationsystem.catalogservice.entity.Material;
import team.nofantasy.recommendationsystem.catalogservice.repository.MaterialRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Loads the catalog file into the database at startup. Running it again updates materials with the same id.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogImporter {
    /**
     * Paid materials may show only a short benefit (case rule), so a longer summary is cut.
     */
    static final int PAID_SUMMARY_LIMIT = 300;

    private final CatalogProperties catalogProperties;
    private final ResourceLoader resourceLoader;
    private final JsonMapper jsonMapper;
    private final MaterialRepository materialRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void importOnStartup() {
        String location = catalogProperties.importPath();

        if (location == null || location.isBlank()) {
            log.atInfo().setMessage("Catalog import is skipped: no import path").log();
            return;
        }

        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.atWarn().setMessage("Catalog import file is not found").addKeyValue("location", location).log();
            return;
        }

        int imported = importFrom(resource);
        log.atInfo().setMessage("Catalog is imported").addKeyValue("materials", imported).log();
    }

    /**
     * @return how many materials were saved; rows without id, title or url are skipped.
     */
    public int importFrom(Resource resource) {
        List<MaterialImportDto> rows;
        try (InputStream input = resource.getInputStream()) {
            rows = jsonMapper.readValue(input, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read catalog file " + resource.getDescription(), e);
        }

        List<Material> materials = rows.stream()
                .filter(CatalogImporter::isValid)
                .map(CatalogImporter::toMaterial)
                .toList();

        if (materials.size() < rows.size()) {
            log.atWarn()
                    .setMessage("Catalog rows without id, title or url are skipped")
                    .addKeyValue("skipped", rows.size() - materials.size())
                    .log();
        }

        materialRepository.saveAll(materials);
        return materials.size();
    }

    private static boolean isValid(MaterialImportDto row) {
        return hasText(row.id()) && hasText(row.title()) && hasText(row.url());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static Material toMaterial(MaterialImportDto row) {
        boolean paid = Boolean.TRUE.equals(row.paid());

        return Material.builder()
                .id(row.id().trim())
                .title(row.title().trim())
                .summary(paid ? shorten(row.summary()) : row.summary())
                .format(row.format())
                .durationMinutes(row.durationMinutes())
                .level(row.level())
                .topics(row.topics() == null ? List.of() : row.topics())
                .businessStages(row.businessStages() == null ? List.of() : row.businessStages())
                .spheres(row.spheres() == null ? List.of() : row.spheres())
                .paid(paid)
                .url(row.url().trim())
                .build();
    }

    private static String shorten(String summary) {
        if (summary == null || summary.length() <= PAID_SUMMARY_LIMIT) {
            return summary;
        }

        return summary.substring(0, PAID_SUMMARY_LIMIT - 1).stripTrailing() + "…";
    }
}
