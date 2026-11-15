package team.nofantasy.recommendationsystem.recommendationservice.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import team.nofantasy.recommendationsystem.recommendationservice.client.MetricClient;
import team.nofantasy.recommendationsystem.recommendationservice.client.ProfileClient;
import team.nofantasy.recommendationsystem.recommendationservice.configuration.properties.RecommendationProperties;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.llm.LlmRankingServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.metric.EventServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.profile.ProfileServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.CandidateServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.HistoryItemServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.RecommendationItemServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.RecommendationListServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.RecommendationListServiceDto.Engine;
import team.nofantasy.recommendationsystem.recommendationservice.exception.CatalogUnavailableException;
import team.nofantasy.recommendationsystem.recommendationservice.exception.LlmRankingException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the personal list: the profile and history of the user, candidates from the catalog, the LLM to choose and
 * explain, then validation. When the LLM can't answer, the list is the topic-match baseline instead, never an error.
 */
@Service
@Slf4j
public class RecommendationService {
    static final String NO_GOAL_QUESTION =
            "Какую задачу в бизнесе вы решаете сейчас? Опишите её в профиле, и подборка станет точнее.";
    static final String NO_MATCH_QUESTION = "Опишите задачу подробнее, чтобы подбор был точнее.";

    /** Events that change what to recommend; impressions and clicks don't. */
    private static final Set<String> HISTORY_EVENTS = Set.of("START", "COMPLETE", "LIKE", "DISLIKE");
    private static final int PROMPT_HISTORY_LIMIT = 20;
    private static final int REASON_LIMIT = 400;
    private static final int NEXT_STEP_LIMIT = 200;
    private static final int QUESTION_LIMIT = 300;
    private static final int EXPLANATION_LIMIT = 400;
    private static final int CACHE_SIZE = 10_000;

    private final ProfileClient profileClient;
    private final MetricClient metricClient;
    private final CatalogService catalogService;
    private final CandidateService candidateService;
    private final LlmRankingService llmRankingService;
    private final ExperimentService experimentService;
    private final RecommendationProperties properties;
    private final Cache<CacheKey, RecommendationListServiceDto> cache;

    public RecommendationService(
            ProfileClient profileClient,
            MetricClient metricClient,
            CatalogService catalogService,
            CandidateService candidateService,
            LlmRankingService llmRankingService,
            ExperimentService experimentService,
            RecommendationProperties properties
    ) {
        this.profileClient = profileClient;
        this.metricClient = metricClient;
        this.catalogService = catalogService;
        this.candidateService = candidateService;
        this.llmRankingService = llmRankingService;
        this.experimentService = experimentService;
        this.properties = properties;
        // A baseline list expires sooner, so a failed LLM call is retried soon but not on every request
        this.cache = Caffeine.newBuilder()
                .maximumSize(CACHE_SIZE)
                .expireAfter(Expiry.writing((CacheKey key, RecommendationListServiceDto list) ->
                        list.engine() == Engine.LLM ? properties.cacheTtl() : properties.fallbackCacheTtl()))
                .build();
    }

    /**
     * The list for the user. It is reused while the profile and the history stay the same, but every response gets
     * its own id.
     *
     * @throws CatalogUnavailableException if catalog-service can't answer and no catalog snapshot is cached.
     */
    public RecommendationListServiceDto getRecommendations(UUID userId) {
        long startedAt = System.nanoTime();
        ProfileServiceDto profile = profileClient.getProfile(userId).orElse(ProfileServiceDto.EMPTY);
        List<EventServiceDto> history = metricClient.getHistory(userId, properties.historyLimit()).stream()
                .filter(event -> event != null && event.materialId() != null && HISTORY_EVENTS.contains(event.type()))
                .toList();
        String variant = experimentService.getVariant(userId);

        CacheKey key = new CacheKey(
                userId,
                variant,
                profile,
                history.stream().map(event -> event.type() + ":" + event.materialId()).toList()
        );
        RecommendationListServiceDto cached = cache.getIfPresent(key);
        RecommendationListServiceDto list;
        if (cached != null) {
            list = withNewId(cached);
        } else {
            list = build(variant, profile, history);
            cache.put(key, list);
        }

        log.atInfo()
                .setMessage("Recommendations are served")
                .addKeyValue("userId", userId)
                .addKeyValue("recommendationId", list.recommendationId())
                .addKeyValue("variant", list.variant())
                .addKeyValue("engine", list.engine())
                .addKeyValue("materialIds", list.items().stream().map(item -> item.material().id()).toList())
                .addKeyValue("cached", cached != null)
                .addKeyValue("latencyMs", Duration.ofNanos(System.nanoTime() - startedAt).toMillis())
                .log();
        return list;
    }

    private RecommendationListServiceDto build(String variant, ProfileServiceDto profile, List<EventServiceDto> history) {
        List<CandidateServiceDto> candidates = candidateService.findCandidates(profile, history);
        if (candidates.isEmpty()) {
            return new RecommendationListServiceDto(
                    UUID.randomUUID(), variant, Engine.BASELINE, baselineQuestion(profile, candidates), null, List.of()
            );
        }

        if (ExperimentService.LLM_VARIANT.equals(variant) && llmRankingService.isAvailable()) {
            try {
                LlmRankingServiceDto ranking = llmRankingService.rank(
                        profile, toHistoryItems(history), candidates, properties.items()
                );
                String question = clean(ranking.clarifyingQuestion(), QUESTION_LIMIT);
                List<RecommendationItemServiceDto> items = toItems(ranking, candidates, profile);
                if (!items.isEmpty()) {
                    String explanation = clean(ranking.orderExplanation(), EXPLANATION_LIMIT);
                    return new RecommendationListServiceDto(
                            UUID.randomUUID(),
                            variant,
                            Engine.LLM,
                            question,
                            explanation != null ? explanation : Explanations.order(chosen(items, candidates), profile),
                            items
                    );
                }

                log.atWarn().setMessage("The LLM chose no valid candidate; answering with the baseline").log();
                return baseline(variant, profile, candidates, question);
            } catch (LlmRankingException e) {
                log.atWarn()
                        .setMessage("LLM ranking failed; answering with the baseline")
                        .addKeyValue("reason", e.getMessage())
                        .log();
            }
        }

        return baseline(variant, profile, candidates, null);
    }

    /**
     * The first candidates in baseline order, explained from their signals.
     */
    private RecommendationListServiceDto baseline(
            String variant,
            ProfileServiceDto profile,
            List<CandidateServiceDto> candidates,
            String question
    ) {
        List<CandidateServiceDto> shown = candidates.subList(0, Math.min(properties.items(), candidates.size()));
        List<RecommendationItemServiceDto> items = new ArrayList<>();
        for (CandidateServiceDto candidate : shown) {
            items.add(new RecommendationItemServiceDto(
                    candidate.material(),
                    Explanations.reason(candidate, profile),
                    Explanations.nextStep(candidate.material()),
                    items.size()
            ));
        }

        return new RecommendationListServiceDto(
                UUID.randomUUID(),
                variant,
                Engine.BASELINE,
                question != null ? question : baselineQuestion(profile, candidates),
                Explanations.order(shown, profile),
                List.copyOf(items)
        );
    }

    /**
     * The candidates behind the items of a list, in the order of the list.
     */
    private static List<CandidateServiceDto> chosen(
            List<RecommendationItemServiceDto> items,
            List<CandidateServiceDto> candidates
    ) {
        Map<String, CandidateServiceDto> candidatesById = new HashMap<>();
        candidates.forEach(candidate -> candidatesById.putIfAbsent(candidate.material().id(), candidate));
        return items.stream().map(item -> candidatesById.get(item.material().id())).toList();
    }

    /**
     * Keeps the picks that are candidates, once each and in the LLM's order. The material always comes from the
     * catalog; a missing reason or next step is written from the signals.
     */
    private List<RecommendationItemServiceDto> toItems(
            LlmRankingServiceDto ranking,
            List<CandidateServiceDto> candidates,
            ProfileServiceDto profile
    ) {
        Map<String, CandidateServiceDto> candidatesById = new HashMap<>();
        candidates.forEach(candidate -> candidatesById.putIfAbsent(candidate.material().id(), candidate));

        List<RecommendationItemServiceDto> items = new ArrayList<>();
        Set<String> chosen = new HashSet<>();
        int dropped = 0;
        for (LlmRankingServiceDto.Pick pick : ranking.items()) {
            if (items.size() == properties.items()) {
                break;
            }

            String id = pick.id() == null ? "" : pick.id().strip();
            CandidateServiceDto candidate = candidatesById.get(id);
            if (candidate == null || !chosen.add(id)) {
                dropped++;
                continue;
            }

            String reason = clean(pick.reason(), REASON_LIMIT);
            String nextStep = clean(pick.nextStep(), NEXT_STEP_LIMIT);
            items.add(new RecommendationItemServiceDto(
                    candidate.material(),
                    reason != null ? reason : Explanations.reason(candidate, profile),
                    nextStep != null ? nextStep : Explanations.nextStep(candidate.material()),
                    items.size()
            ));
        }

        if (dropped > 0) {
            log.atWarn()
                    .setMessage("The LLM answer had picks that aren't candidates or repeat; they were dropped")
                    .addKeyValue("dropped", dropped)
                    .log();
        }
        return List.copyOf(items);
    }

    private List<HistoryItemServiceDto> toHistoryItems(List<EventServiceDto> history) {
        Map<String, MaterialServiceDto> catalog = catalogService.getMaterials();
        return history.stream()
                .limit(PROMPT_HISTORY_LIMIT)
                .map(event -> {
                    MaterialServiceDto material = catalog.get(event.materialId());
                    return new HistoryItemServiceDto(event.type(), event.materialId(), material == null ? null : material.title());
                })
                .toList();
    }

    private static String baselineQuestion(ProfileServiceDto profile, List<CandidateServiceDto> candidates) {
        if (!StringUtils.hasText(profile.goal())) {
            return NO_GOAL_QUESTION;
        }
        return candidates.stream().noneMatch(candidate -> candidate.topicMatch() > 0) ? NO_MATCH_QUESTION : null;
    }

    static String clean(String text, int limit) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        String stripped = text.strip();
        return stripped.length() <= limit ? stripped : stripped.substring(0, limit - 1).stripTrailing() + "…";
    }

    private static RecommendationListServiceDto withNewId(RecommendationListServiceDto list) {
        return new RecommendationListServiceDto(
                UUID.randomUUID(),
                list.variant(),
                list.engine(),
                list.clarifyingQuestion(),
                list.orderExplanation(),
                list.items()
        );
    }

    private record CacheKey(UUID userId, String variant, ProfileServiceDto profile, List<String> history) {
    }
}
