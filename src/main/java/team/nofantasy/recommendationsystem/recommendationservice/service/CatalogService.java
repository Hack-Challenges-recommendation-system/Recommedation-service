package team.nofantasy.recommendationsystem.recommendationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.nofantasy.recommendationsystem.recommendationservice.client.CatalogClient;
import team.nofantasy.recommendationsystem.recommendationservice.configuration.properties.RecommendationProperties;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialSearchResultServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.exception.CatalogUnavailableException;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The catalog kept in memory. Links and paid content in a list always come from here, never from the LLM.
 */
@Service
@Slf4j
public class CatalogService {
    private final CatalogClient catalogClient;
    private final Duration ttl;
    private final ReentrantLock reloadLock = new ReentrantLock();
    private volatile Snapshot snapshot;

    public CatalogService(CatalogClient catalogClient, RecommendationProperties properties) {
        this.catalogClient = catalogClient;
        this.ttl = properties.catalogTtl();
    }

    /**
     * Every material by id, in catalog order. Reloaded after {@code catalog-ttl}; if catalog-service fails then, the
     * previous snapshot is used.
     *
     * @throws CatalogUnavailableException if there is no snapshot yet and catalog-service can't answer.
     */
    public Map<String, MaterialServiceDto> getMaterials() {
        Snapshot current = snapshot;
        if (current != null && !current.isExpired(ttl)) {
            return current.materials();
        }

        reloadLock.lock();
        try {
            current = snapshot;
            if (current != null && !current.isExpired(ttl)) {
                return current.materials();
            }

            Map<String, MaterialServiceDto> materials = load();
            // An empty catalog may still be importing, so it is asked again on the next request
            if (!materials.isEmpty()) {
                snapshot = new Snapshot(materials, System.nanoTime());
            }

            return materials;
        } catch (CatalogUnavailableException e) {
            if (current == null) {
                throw e;
            }

            log.atWarn()
                    .setMessage("Catalog is unavailable; using the previous snapshot")
                    .addKeyValue("materials", current.materials().size())
                    .log();
            return current.materials();
        } finally {
            reloadLock.unlock();
        }
    }

    /**
     * Full-text search in catalog-service; empty if it can't answer.
     */
    public List<MaterialSearchResultServiceDto> search(String query, int limit) {
        return catalogClient.search(query, limit);
    }

    private Map<String, MaterialServiceDto> load() {
        Map<String, MaterialServiceDto> materials = new LinkedHashMap<>();
        for (MaterialServiceDto material : catalogClient.getAllMaterials()) {
            if (material != null && material.id() != null) {
                materials.putIfAbsent(material.id(), material);
            }
        }

        log.atInfo()
                .setMessage("Catalog is loaded")
                .addKeyValue("materials", materials.size())
                .log();
        return Collections.unmodifiableMap(materials);
    }

    private record Snapshot(Map<String, MaterialServiceDto> materials, long loadedAtNanos) {
        boolean isExpired(Duration ttl) {
            return System.nanoTime() - loadedAtNanos > ttl.toNanos();
        }
    }
}
