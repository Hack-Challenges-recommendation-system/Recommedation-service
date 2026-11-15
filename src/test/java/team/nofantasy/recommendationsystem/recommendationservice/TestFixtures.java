package team.nofantasy.recommendationsystem.recommendationservice;

import org.springframework.core.io.ClassPathResource;
import team.nofantasy.recommendationsystem.recommendationservice.configuration.properties.RecommendationProperties;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.CandidateServiceDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The synthetic catalog in {@code fixtures/materials.json} and settings for tests.
 */
public final class TestFixtures {
    public static final JsonMapper JSON = JsonMapper.builder().build();

    private TestFixtures() {
    }

    public static String materialsJson() {
        try {
            return new ClassPathResource("fixtures/materials.json").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<MaterialServiceDto> materials() {
        return JSON.readValue(materialsJson(), new TypeReference<>() {
        });
    }

    public static Map<String, MaterialServiceDto> catalog() {
        Map<String, MaterialServiceDto> catalog = new LinkedHashMap<>();
        materials().forEach(material -> catalog.put(material.id(), material));
        return catalog;
    }

    public static MaterialServiceDto material(String id) {
        MaterialServiceDto material = catalog().get(id);
        if (material == null) {
            throw new IllegalArgumentException("No material " + id + " in the fixture");
        }
        return material;
    }

    public static CandidateServiceDto candidate(String id, double topicMatch) {
        return new CandidateServiceDto(material(id), topicMatch, 0, null, false, false);
    }

    public static RecommendationProperties properties(int items, int maxCandidates, int llmShare) {
        return new RecommendationProperties(
                items,
                maxCandidates,
                100,
                Duration.ofMinutes(30),
                Duration.ofMinutes(2),
                Duration.ofMinutes(10),
                "medium",
                llmShare
        );
    }

    public static RecommendationProperties properties(int llmShare) {
        return properties(6, 40, llmShare);
    }
}
