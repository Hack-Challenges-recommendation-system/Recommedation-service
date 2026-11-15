package team.nofantasy.recommendationsystem.recommendationservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import team.nofantasy.recommendationsystem.recommendationservice.configuration.properties.ServicesProperties;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.metric.EventServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.metric.MaterialUsersServiceDto;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Reads the user's history and popularity signals from metric-service. Every call is best effort: without metrics the
 * list is still built, just with fewer signals.
 */
@Component
@Slf4j
public class MetricClient {
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final ParameterizedTypeReference<List<EventServiceDto>> EVENTS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<MaterialUsersServiceDto>> MATERIAL_USERS =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public MetricClient(RestClient.Builder restClientBuilder, ServicesProperties servicesProperties) {
        this.restClient = restClientBuilder.baseUrl(servicesProperties.metricUrl()).build();
    }

    /**
     * The newest events of the user, newest first.
     */
    public List<EventServiceDto> getHistory(UUID userId, int limit) {
        return orEmpty("History", () -> restClient.get()
                .uri("/api/v1/events/me?limit={limit}", Math.clamp(limit, 1, 500))
                .header(USER_ID_HEADER, userId.toString())
                .retrieve()
                .body(EVENTS));
    }

    /**
     * Materials that the most distinct users started or completed.
     */
    public List<MaterialUsersServiceDto> getPopularMaterials(int limit) {
        return orEmpty("Popular materials", () -> restClient.get()
                .uri("/api/v1/metrics/materials/popular?limit={limit}", Math.clamp(limit, 1, 200))
                .retrieve()
                .body(MATERIAL_USERS));
    }

    /**
     * What users started or completed after this material.
     */
    public List<MaterialUsersServiceDto> getMaterialsTakenNext(String materialId, int limit) {
        return orEmpty("Materials taken next", () -> restClient.get()
                .uri("/api/v1/metrics/materials/{materialId}/next?limit={limit}", materialId, Math.clamp(limit, 1, 100))
                .retrieve()
                .body(MATERIAL_USERS));
    }

    private static <T> List<T> orEmpty(String what, Supplier<List<T>> call) {
        try {
            List<T> result = call.get();
            return result == null ? List.of() : result;
        } catch (RestClientException e) {
            log.atWarn()
                    .setMessage(what + " are unavailable; recommending without them")
                    .addKeyValue("error", e.getMessage())
                    .log();
            return List.of();
        }
    }
}
