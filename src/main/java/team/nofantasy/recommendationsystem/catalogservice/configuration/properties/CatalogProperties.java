package team.nofantasy.recommendationsystem.catalogservice.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param importPath location of the catalog file imported at startup, e.g. {@code file:/data/catalog.json};
 *                   empty skips the import. The case data is mounted at runtime and never committed.
 */
@ConfigurationProperties("app.catalog")
public record CatalogProperties(
        String importPath
) {
}
