package team.nofantasy.recommendationsystem.recommendationservice.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the services that recommendation-service reads from live. Their paths start with {@code /api/v1}.
 *
 * @param profileUrl base URL of profile-service, e.g. {@code http://profile-service:8080}.
 * @param catalogUrl base URL of catalog-service.
 * @param metricUrl base URL of metric-service.
 */
@ConfigurationProperties("app.services")
public record ServicesProperties(
        String profileUrl,
        String catalogUrl,
        String metricUrl
) {
}
