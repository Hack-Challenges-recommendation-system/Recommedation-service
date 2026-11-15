package team.nofantasy.recommendationsystem.recommendationservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import team.nofantasy.recommendationsystem.recommendationservice.configuration.properties.ServicesProperties;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialPageServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialSearchResultServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.exception.CatalogUnavailableException;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads materials from catalog-service.
 */
@Component
@Slf4j
public class CatalogClient {
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 100;
    private static final int MAX_QUERY_LENGTH = 500;
    private static final ParameterizedTypeReference<List<MaterialSearchResultServiceDto>> SEARCH_RESULTS =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public CatalogClient(RestClient.Builder restClientBuilder, ServicesProperties servicesProperties) {
        this.restClient = restClientBuilder.baseUrl(servicesProperties.catalogUrl()).build();
    }

    /**
     * Every material of the catalog, page by page.
     *
     * @throws CatalogUnavailableException if catalog-service can't answer.
     */
    public List<MaterialServiceDto> getAllMaterials() {
        List<MaterialServiceDto> materials = new ArrayList<>();
        try {
            for (int page = 0; page < MAX_PAGES; page++) {
                MaterialPageServiceDto result = restClient.get()
                        .uri("/api/v1/materials?page={page}&size={size}", page, PAGE_SIZE)
                        .retrieve()
                        .body(MaterialPageServiceDto.class);
                if (result == null || result.items() == null) {
                    break;
                }

                materials.addAll(result.items());
                if (page + 1 >= result.totalPages()) {
                    break;
                }
            }
        } catch (RestClientException e) {
            throw new CatalogUnavailableException("Catalog is unavailable: " + e.getMessage(), e);
        }

        return materials;
    }

    /**
     * Full-text search, best match first; empty if catalog-service can't answer.
     */
    public List<MaterialSearchResultServiceDto> search(String query, int limit) {
        String q = query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) : query;
        try {
            List<MaterialSearchResultServiceDto> results = restClient.get()
                    .uri("/api/v1/materials/search?q={q}&limit={limit}", q, Math.clamp(limit, 1, 100))
                    .retrieve()
                    .body(SEARCH_RESULTS);
            return results == null ? List.of() : results;
        } catch (RestClientException e) {
            log.atWarn()
                    .setMessage("Catalog search is unavailable; recommending without topic matching")
                    .addKeyValue("error", e.getMessage())
                    .log();
            return List.of();
        }
    }
}
