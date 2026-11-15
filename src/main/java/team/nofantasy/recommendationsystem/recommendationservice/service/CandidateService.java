package team.nofantasy.recommendationsystem.recommendationservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import team.nofantasy.recommendationsystem.recommendationservice.client.MetricClient;
import team.nofantasy.recommendationsystem.recommendationservice.configuration.properties.RecommendationProperties;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialSearchResultServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.metric.EventServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.metric.MaterialUsersServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.profile.ProfileServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.CandidateServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.exception.CatalogUnavailableException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chooses the materials a list may contain. Sorted by topic match with the goal, the candidates are the topic-match
 * baseline that the case compares recommendations against, and the fallback when the LLM can't answer.
 */
@Service
@RequiredArgsConstructor
public class CandidateService {
    /**
     * Baseline order: topic match with the goal first, then "taken after" the user's materials, the user's stage,
     * a preferred format and popularity.
     */
    static final Comparator<CandidateServiceDto> BASELINE_ORDER = Comparator
            .comparingDouble(CandidateServiceDto::topicMatch).reversed()
            .thenComparing(candidate -> candidate.takenAfter() == null)
            .thenComparing(candidate -> !candidate.stageMatch())
            .thenComparing(candidate -> !candidate.formatMatch())
            .thenComparing(Comparator.comparingLong(CandidateServiceDto::popularUsers).reversed());

    /** Events after which a material is not recommended again. */
    private static final Set<String> FINISHED = Set.of("COMPLETE", "DISLIKE");
    /** Events whose materials tell what to take next. */
    private static final Set<String> ENGAGED = Set.of("START", "COMPLETE", "LIKE");
    private static final int POPULAR_LIMIT = 20;
    private static final int NEXT_SOURCES = 3;
    private static final int NEXT_LIMIT = 10;

    private final CatalogService catalogService;
    private final MetricClient metricClient;
    private final RecommendationProperties properties;

    /**
     * Up to {@code max-candidates} materials in baseline order: those matching the goal, those often taken after the
     * user's recent materials, popular ones and, to fill the rest, those for the user's stage and formats. Completed
     * and disliked materials are left out.
     *
     * @param history events of the user, newest first.
     * @throws CatalogUnavailableException if the catalog can't be read.
     */
    public List<CandidateServiceDto> findCandidates(ProfileServiceDto profile, List<EventServiceDto> history) {
        Map<String, MaterialServiceDto> catalog = catalogService.getMaterials();
        Set<String> finished = history.stream()
                .filter(event -> FINISHED.contains(event.type()))
                .map(EventServiceDto::materialId)
                .collect(Collectors.toSet());

        Map<String, Double> topicMatch = findTopicMatches(profile);
        Map<String, String> takenAfter = findTakenAfter(history, catalog);
        Map<String, Long> popularity = findPopular();

        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(topicMatch.keySet());
        ids.addAll(takenAfter.keySet());
        ids.addAll(popularity.keySet());
        ids.removeIf(id -> !catalog.containsKey(id) || finished.contains(id));

        int max = properties.maxCandidates();
        if (ids.size() < max) {
            catalog.values().stream()
                    .filter(material -> !ids.contains(material.id()) && !finished.contains(material.id()))
                    .sorted(Comparator.comparing((MaterialServiceDto material) -> !isStageMatch(material, profile))
                            .thenComparing(material -> !isFormatMatch(material, profile))
                            .thenComparing(Comparator.comparingLong(
                                    (MaterialServiceDto material) -> popularity.getOrDefault(material.id(), 0L)
                            ).reversed()))
                    .limit(max - ids.size())
                    .toList()
                    .forEach(material -> ids.add(material.id()));
        }

        return ids.stream()
                .map(id -> new CandidateServiceDto(
                        catalog.get(id),
                        topicMatch.getOrDefault(id, 0.0),
                        popularity.getOrDefault(id, 0L),
                        takenAfter.get(id),
                        isStageMatch(catalog.get(id), profile),
                        isFormatMatch(catalog.get(id), profile)
                ))
                .sorted(BASELINE_ORDER)
                .limit(max)
                .toList();
    }

    static boolean isStageMatch(MaterialServiceDto material, ProfileServiceDto profile) {
        return profile.businessStage() != null && material.businessStages().contains(profile.businessStage());
    }

    static boolean isFormatMatch(MaterialServiceDto material, ProfileServiceDto profile) {
        return material.format() != null && profile.preferredFormats().contains(material.format());
    }

    /**
     * Search scores by material id, best first. The query is the goal, or the sphere when the goal is empty.
     */
    private Map<String, Double> findTopicMatches(ProfileServiceDto profile) {
        String query = StringUtils.hasText(profile.goal()) ? profile.goal() : profile.businessSphere();
        Map<String, Double> scores = new LinkedHashMap<>();
        if (!StringUtils.hasText(query)) {
            return scores;
        }

        for (MaterialSearchResultServiceDto result : catalogService.search(query.strip(), properties.maxCandidates())) {
            if (result != null && result.material() != null && result.score() > 0) {
                scores.merge(result.material().id(), result.score(), Math::max);
            }
        }

        return scores;
    }

    /**
     * Materials that users take after the user's recent ones, with the title of the material they follow.
     */
    private Map<String, String> findTakenAfter(List<EventServiceDto> history, Map<String, MaterialServiceDto> catalog) {
        List<String> recent = history.stream()
                .filter(event -> ENGAGED.contains(event.type()) && catalog.containsKey(event.materialId()))
                .map(EventServiceDto::materialId)
                .distinct()
                .limit(NEXT_SOURCES)
                .toList();

        Map<String, String> takenAfter = new LinkedHashMap<>();
        for (String source : recent) {
            String title = catalog.get(source).title();
            for (MaterialUsersServiceDto next : metricClient.getMaterialsTakenNext(source, NEXT_LIMIT)) {
                if (next != null && next.materialId() != null && !next.materialId().equals(source)) {
                    takenAfter.putIfAbsent(next.materialId(), title);
                }
            }
        }

        return takenAfter;
    }

    private Map<String, Long> findPopular() {
        Map<String, Long> popularity = new LinkedHashMap<>();
        for (MaterialUsersServiceDto material : metricClient.getPopularMaterials(POPULAR_LIMIT)) {
            if (material != null && material.materialId() != null) {
                popularity.merge(material.materialId(), material.users(), Math::max);
            }
        }

        return popularity;
    }
}
