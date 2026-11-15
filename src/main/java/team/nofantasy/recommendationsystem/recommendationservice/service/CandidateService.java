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
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.CandidateServiceDto.HistorySource;
import team.nofantasy.recommendationsystem.recommendationservice.exception.CatalogUnavailableException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Chooses the materials a list may contain. Sorted by topic match with the goal, the candidates are the topic-match
 * baseline that the case compares recommendations against, and the fallback when the LLM can't answer.
 */
@Service
@RequiredArgsConstructor
public class CandidateService {
    /**
     * Baseline order: topic match with the goal first, then "taken after" and "similar to" the user's materials, the
     * user's stage, a preferred format and popularity.
     */
    static final Comparator<CandidateServiceDto> BASELINE_ORDER = Comparator
            .comparingDouble(CandidateServiceDto::topicMatch).reversed()
            .thenComparing(candidate -> candidate.takenAfter() == null)
            .thenComparing(candidate -> candidate.similarTo() == null)
            .thenComparing(candidate -> !candidate.stageMatch())
            .thenComparing(candidate -> !candidate.formatMatch())
            .thenComparing(Comparator.comparingLong(CandidateServiceDto::popularUsers).reversed());

    private static final String LIKE = "LIKE";
    private static final String DISLIKE = "DISLIKE";
    private static final String COMPLETE = "COMPLETE";
    /** Events whose materials tell what to take next, strongest first. */
    private static final List<String> ENGAGED = List.of(LIKE, COMPLETE, "START");
    private static final int POPULAR_LIMIT = 20;
    private static final int HISTORY_SOURCES = 3;
    private static final int NEXT_LIMIT = 10;
    private static final int SIMILAR_PER_SOURCE = 4;
    /**
     * Shares of the candidates, in percent, that the history and popularity keep, so they reach the LLM even when the
     * goal matches more materials than there are candidates. Of 40 candidates: 8, 6 and 4.
     */
    private static final int SIMILAR_SHARE = 20;
    private static final int NEXT_SHARE = 15;
    private static final int POPULAR_SHARE = 10;

    private final CatalogService catalogService;
    private final MetricClient metricClient;
    private final RecommendationProperties properties;

    /**
     * Up to {@code max-candidates} materials in baseline order: materials similar to and often taken after the user's
     * recent ones, those matching the goal, popular ones and, to fill the rest, those for the user's stage and formats
     * on different topics. Completed materials and those the user disliked last are left out.
     *
     * @param history events of the user, newest first.
     * @throws CatalogUnavailableException if the catalog can't be read.
     */
    public List<CandidateServiceDto> findCandidates(ProfileServiceDto profile, List<EventServiceDto> history) {
        Map<String, MaterialServiceDto> catalog = catalogService.getMaterials();
        Map<String, String> reactions = latestReactions(history);
        Set<String> finished = new HashSet<>();
        history.stream().filter(event -> COMPLETE.equals(event.type())).forEach(event -> finished.add(event.materialId()));
        reactions.forEach((id, reaction) -> {
            if (DISLIKE.equals(reaction)) {
                finished.add(id);
            }
        });

        Map<String, String> sources = recentSources(history, catalog, reactions);
        Map<String, Double> topicMatch = findTopicMatches(profile);
        Map<String, String> takenAfter = findTakenAfter(sources, catalog);
        Map<String, HistorySource> similarTo = findSimilar(sources, catalog, finished);
        Map<String, Long> popularity = findPopular();

        int max = properties.maxCandidates();
        Set<String> ids = new LinkedHashSet<>();
        Predicate<String> allowed = id -> catalog.containsKey(id) && !finished.contains(id);
        addUpTo(ids, similarTo.keySet(), allowed, max * SIMILAR_SHARE / 100, max);
        addUpTo(ids, takenAfter.keySet(), allowed, max * NEXT_SHARE / 100, max);
        long popularLeft = popularity.keySet().stream().filter(allowed.and(id -> !ids.contains(id))).count();
        int popularSlots = (int) Math.min(max * POPULAR_SHARE / 100, popularLeft);
        addUpTo(ids, topicMatch.keySet(), allowed, max - ids.size() - popularSlots, max);
        addUpTo(ids, popularity.keySet(), allowed, topicMatch.isEmpty() ? max : popularSlots, max);
        fill(catalog, ids, finished, profile, popularity, max - ids.size()).forEach(material -> ids.add(material.id()));

        return ids.stream()
                .map(id -> new CandidateServiceDto(
                        catalog.get(id),
                        topicMatch.getOrDefault(id, 0.0),
                        popularity.getOrDefault(id, 0L),
                        takenAfter.get(id),
                        similarTo.get(id),
                        isStageMatch(catalog.get(id), profile),
                        isFormatMatch(catalog.get(id), profile)
                ))
                .sorted(BASELINE_ORDER)
                .toList();
    }

    static boolean isStageMatch(MaterialServiceDto material, ProfileServiceDto profile) {
        return profile.businessStage() != null && material.businessStages().contains(profile.businessStage());
    }

    static boolean isFormatMatch(MaterialServiceDto material, ProfileServiceDto profile) {
        return material.format() != null && profile.preferredFormats().contains(material.format());
    }

    /**
     * The search text: the goal together with the sphere, or the sphere alone when the goal is empty.
     */
    static String topicQuery(ProfileServiceDto profile) {
        String goal = StringUtils.hasText(profile.goal()) ? profile.goal().strip() : "";
        String sphere = StringUtils.hasText(profile.businessSphere()) ? profile.businessSphere().strip() : "";
        if (goal.isEmpty() || sphere.isEmpty()) {
            return goal.isEmpty() ? sphere : goal;
        }

        return goal.toLowerCase(Locale.ROOT).contains(sphere.toLowerCase(Locale.ROOT)) ? goal : goal + ". " + sphere;
    }

    /**
     * Search scores by material id, best first.
     */
    private Map<String, Double> findTopicMatches(ProfileServiceDto profile) {
        String query = topicQuery(profile);
        Map<String, Double> scores = new LinkedHashMap<>();
        if (query.isEmpty()) {
            return scores;
        }

        for (MaterialSearchResultServiceDto result : catalogService.search(query, properties.maxCandidates())) {
            if (result != null && result.material() != null && result.score() > 0) {
                scores.merge(result.material().id(), result.score(), Math::max);
            }
        }

        return scores;
    }

    /**
     * The newest like or dislike of every material; a later one replaces an earlier one.
     */
    private static Map<String, String> latestReactions(List<EventServiceDto> history) {
        Map<String, String> reactions = new HashMap<>();
        for (EventServiceDto event : history) {
            if (LIKE.equals(event.type()) || DISLIKE.equals(event.type())) {
                reactions.putIfAbsent(event.materialId(), event.type());
            }
        }

        return reactions;
    }

    /**
     * The user's most recent materials with the strongest thing the user did with each: liked, completed or started.
     * Materials the user disliked last don't count.
     */
    private static Map<String, String> recentSources(
            List<EventServiceDto> history,
            Map<String, MaterialServiceDto> catalog,
            Map<String, String> reactions
    ) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (EventServiceDto event : history) {
            String id = event.materialId();
            int strength = ENGAGED.indexOf(event.type());
            if (strength < 0 || !catalog.containsKey(id) || DISLIKE.equals(reactions.get(id))) {
                continue;
            }

            String known = sources.get(id);
            if (known != null) {
                if (strength < ENGAGED.indexOf(known)) {
                    sources.put(id, event.type());
                }
            } else if (sources.size() < HISTORY_SOURCES) {
                sources.put(id, event.type());
            }
        }

        return sources;
    }

    /**
     * Materials that users take after the user's recent ones, with the title of the material they follow.
     */
    private Map<String, String> findTakenAfter(Map<String, String> sources, Map<String, MaterialServiceDto> catalog) {
        Map<String, String> takenAfter = new LinkedHashMap<>();
        for (String source : sources.keySet()) {
            String title = catalog.get(source).title();
            for (MaterialUsersServiceDto next : metricClient.getMaterialsTakenNext(source, NEXT_LIMIT)) {
                if (next != null && next.materialId() != null && !sources.containsKey(next.materialId())) {
                    takenAfter.putIfAbsent(next.materialId(), title);
                }
            }
        }

        return takenAfter;
    }

    /**
     * Materials that share the most topics with each of the user's recent ones. A topic counts more the fewer
     * materials have it, so a rare shared topic beats a common one.
     */
    private static Map<String, HistorySource> findSimilar(
            Map<String, String> sources,
            Map<String, MaterialServiceDto> catalog,
            Set<String> finished
    ) {
        Map<String, HistorySource> similar = new LinkedHashMap<>();
        if (sources.isEmpty()) {
            return similar;
        }

        Map<String, Integer> materialsWithTopic = new HashMap<>();
        catalog.values().forEach(material -> Set.copyOf(material.topics())
                .forEach(topic -> materialsWithTopic.merge(topic, 1, Integer::sum)));

        sources.forEach((sourceId, event) -> {
            MaterialServiceDto source = catalog.get(sourceId);
            Set<String> topics = Set.copyOf(source.topics());
            if (topics.isEmpty()) {
                return;
            }

            catalog.values().stream()
                    .filter(material -> !sources.containsKey(material.id()) && !finished.contains(material.id())
                            && !similar.containsKey(material.id()))
                    .map(material -> new Scored(
                            material,
                            Set.copyOf(material.topics()).stream()
                                    .filter(topics::contains)
                                    .mapToDouble(topic -> Math.log((double) catalog.size() / materialsWithTopic.get(topic)))
                                    .sum(),
                            material.format() != null && material.format().equals(source.format())
                    ))
                    .filter(scored -> scored.score() > 0)
                    .sorted(Comparator.comparingDouble(Scored::score).reversed()
                            .thenComparing(scored -> !scored.sameFormat()))
                    .limit(SIMILAR_PER_SOURCE)
                    .forEach(scored -> similar.put(scored.material().id(), new HistorySource(source.title(), event)));
        });

        return similar;
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

    /**
     * Adds up to {@code count} allowed ids from {@code source}, in its order, while the set holds fewer than {@code max}.
     */
    private static void addUpTo(Set<String> ids, Collection<String> source, Predicate<String> allowed, int count, int max) {
        int added = 0;
        for (String id : source) {
            if (added >= count || ids.size() >= max) {
                return;
            }
            if (allowed.test(id) && ids.add(id)) {
                added++;
            }
        }
    }

    /**
     * The rest of the catalog: the user's stage and formats first, then popular, beginner-friendly and short materials.
     * Within each tier of stage and format match, materials take turns by their first topic, so a new user with an
     * empty profile gets a starter set on different topics.
     */
    private static List<MaterialServiceDto> fill(
            Map<String, MaterialServiceDto> catalog,
            Set<String> ids,
            Set<String> finished,
            ProfileServiceDto profile,
            Map<String, Long> popularity,
            int count
    ) {
        if (count <= 0) {
            return List.of();
        }

        List<MaterialServiceDto> sorted = catalog.values().stream()
                .filter(material -> !ids.contains(material.id()) && !finished.contains(material.id()))
                .sorted(Comparator.comparing((MaterialServiceDto material) -> !isStageMatch(material, profile))
                        .thenComparing(material -> !isFormatMatch(material, profile))
                        .thenComparing(Comparator.comparingLong(
                                (MaterialServiceDto material) -> popularity.getOrDefault(material.id(), 0L)
                        ).reversed())
                        .thenComparing(material -> !"BEGINNER".equals(material.level()))
                        .thenComparing(material -> material.durationMinutes() == null
                                ? Integer.MAX_VALUE
                                : material.durationMinutes()))
                .toList();

        Map<String, Integer> turns = new HashMap<>();
        List<Turn> ordered = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            MaterialServiceDto material = sorted.get(index);
            int tier = (isStageMatch(material, profile) ? 0 : 2) + (isFormatMatch(material, profile) ? 0 : 1);
            String topic = material.topics().isEmpty() ? "" : material.topics().getFirst();
            int turn = turns.merge(tier + "|" + topic, 1, Integer::sum) - 1;
            ordered.add(new Turn(material, tier, turn, index));
        }

        return ordered.stream()
                .sorted(Comparator.comparingInt(Turn::tier).thenComparingInt(Turn::turn).thenComparingInt(Turn::index))
                .limit(count)
                .map(Turn::material)
                .toList();
    }

    /**
     * A catalog material and how much it shares with a material of the history.
     */
    private record Scored(MaterialServiceDto material, double score, boolean sameFormat) {
    }

    /**
     * A material of the fill: its tier of stage and format match, its turn among materials of its first topic and its
     * place in the sorted catalog.
     */
    private record Turn(MaterialServiceDto material, int tier, int turn, int index) {
    }
}
