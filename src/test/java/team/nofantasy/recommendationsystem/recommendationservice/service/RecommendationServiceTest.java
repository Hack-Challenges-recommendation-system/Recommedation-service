package team.nofantasy.recommendationsystem.recommendationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.nofantasy.recommendationsystem.recommendationservice.TestFixtures;
import team.nofantasy.recommendationsystem.recommendationservice.client.MetricClient;
import team.nofantasy.recommendationsystem.recommendationservice.client.ProfileClient;
import team.nofantasy.recommendationsystem.recommendationservice.configuration.properties.RecommendationProperties;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.llm.LlmRankingServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.llm.LlmRankingServiceDto.Pick;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.metric.EventServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.profile.ProfileServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.CandidateServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.RecommendationItemServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.RecommendationListServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.RecommendationListServiceDto.Engine;
import team.nofantasy.recommendationsystem.recommendationservice.exception.LlmRankingException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    private static final ProfileServiceDto PROFILE = new ProfileServiceDto(
            "LAUNCH", "Розничная торговля", "NONE", "найти первых клиентов", List.of("VIDEO"), Instant.EPOCH
    );
    private static final List<CandidateServiceDto> CANDIDATES = List.of(
            TestFixtures.candidate("m-001", 0.6),
            TestFixtures.candidate("m-008", 0.4),
            TestFixtures.candidate("m-005", 0.2),
            TestFixtures.candidate("m-003", 0)
    );

    @Mock
    private ProfileClient profileClient;
    @Mock
    private MetricClient metricClient;
    @Mock
    private CatalogService catalogService;
    @Mock
    private CandidateService candidateService;
    @Mock
    private LlmRankingService llmRankingService;

    private final UUID userId = UUID.randomUUID();

    /**
     * Lists of 3 items; {@code llmShare} percent of users are in the llm variant.
     */
    private RecommendationService service(int llmShare) {
        RecommendationProperties properties = TestFixtures.properties(3, 40, llmShare);
        return new RecommendationService(
                profileClient, metricClient, catalogService, candidateService, llmRankingService,
                new ExperimentService(properties), properties
        );
    }

    private void givenUser(ProfileServiceDto profile, List<CandidateServiceDto> candidates) {
        when(profileClient.getProfile(userId)).thenReturn(Optional.ofNullable(profile));
        when(metricClient.getHistory(eq(userId), anyInt()))
                .thenReturn(List.of(new EventServiceDto("IMPRESSION", "m-004", Instant.EPOCH)));
        when(candidateService.findCandidates(any(), any())).thenReturn(candidates);
    }

    private void givenLlmAnswers(LlmRankingServiceDto ranking) {
        when(llmRankingService.isAvailable()).thenReturn(true);
        when(catalogService.getMaterials()).thenReturn(TestFixtures.catalog());
        when(llmRankingService.rank(any(), any(), any(), anyInt())).thenReturn(ranking);
    }

    private static List<String> ids(RecommendationListServiceDto list) {
        return list.items().stream().map(item -> item.material().id()).toList();
    }

    @Test
    void llmAnswerIsCheckedAgainstTheCandidates() {
        givenUser(PROFILE, CANDIDATES);
        givenLlmAnswers(new LlmRankingServiceDto(List.of(
                new Pick("m-005", "Бесплатное продвижение для первых покупателей", "Опубликуйте первый пост"),
                new Pick("m-404", "Такого материала нет", "-"),
                new Pick(" m-001 ", "  ", ""),
                new Pick("m-005", "Повтор", "Повтор"),
                new Pick("m-008", "Скрипты для первых звонков", "Позвоните трём клиентам"),
                new Pick("m-003", "Не помещается в список", "-")
        ), ""));

        RecommendationListServiceDto list = service(100).getRecommendations(userId);

        assertThat(list.engine()).isEqualTo(Engine.LLM);
        assertThat(list.variant()).isEqualTo(ExperimentService.LLM_VARIANT);
        assertThat(list.clarifyingQuestion()).isNull();
        assertThat(ids(list)).containsExactly("m-005", "m-001", "m-008");
        assertThat(list.items()).extracting(RecommendationItemServiceDto::position).containsExactly(0, 1, 2);
        assertThat(list.items().getFirst().reason()).isEqualTo("Бесплатное продвижение для первых покупателей");
        // blank reason and next step are written from the signals
        assertThat(list.items().get(1).reason()).isEqualTo("Совпадает с вашей задачей по темам: маркетинг, продажи");
        assertThat(list.items().get(1).nextStep()).isEqualTo("Прочитайте и выпишите одну идею, которую примените на этой неделе");
    }

    @Test
    void failedLlmGivesTheBaseline() {
        givenUser(PROFILE, CANDIDATES);
        when(llmRankingService.isAvailable()).thenReturn(true);
        when(catalogService.getMaterials()).thenReturn(TestFixtures.catalog());
        when(llmRankingService.rank(any(), any(), any(), anyInt())).thenThrow(new LlmRankingException("HTTP 529"));

        RecommendationListServiceDto list = service(100).getRecommendations(userId);

        assertThat(list.engine()).isEqualTo(Engine.BASELINE);
        assertThat(list.variant()).isEqualTo(ExperimentService.LLM_VARIANT);
        assertThat(ids(list)).containsExactly("m-001", "m-008", "m-005");
        assertThat(list.items()).allSatisfy(item -> {
            assertThat(item.reason()).isNotBlank();
            assertThat(item.nextStep()).isNotBlank();
        });
        assertThat(list.clarifyingQuestion()).isNull();
    }

    @Test
    void withoutKeyTheLlmIsNeverAsked() {
        givenUser(PROFILE, CANDIDATES);
        when(llmRankingService.isAvailable()).thenReturn(false);

        RecommendationListServiceDto list = service(100).getRecommendations(userId);

        assertThat(list.engine()).isEqualTo(Engine.BASELINE);
        verify(llmRankingService, never()).rank(any(), any(), any(), anyInt());
    }

    @Test
    void noValidPickGivesTheBaselineWithTheQuestionOfTheLlm() {
        givenUser(PROFILE, CANDIDATES);
        givenLlmAnswers(new LlmRankingServiceDto(List.of(new Pick("m-404", "-", "-")), "Что продаёт ваш магазин?"));

        RecommendationListServiceDto list = service(100).getRecommendations(userId);

        assertThat(list.engine()).isEqualTo(Engine.BASELINE);
        assertThat(list.clarifyingQuestion()).isEqualTo("Что продаёт ваш магазин?");
        assertThat(ids(list)).containsExactly("m-001", "m-008", "m-005");
    }

    @Test
    void sameProfileAndHistoryAreServedFromTheCache() {
        givenUser(PROFILE, CANDIDATES);
        givenLlmAnswers(new LlmRankingServiceDto(List.of(new Pick("m-005", "Причина", "Шаг")), ""));
        RecommendationService service = service(100);

        RecommendationListServiceDto first = service.getRecommendations(userId);
        RecommendationListServiceDto second = service.getRecommendations(userId);

        verify(llmRankingService, times(1)).rank(any(), any(), any(), anyInt());
        assertThat(second.items()).isEqualTo(first.items());
        assertThat(second.recommendationId()).as("every shown list has its own id").isNotEqualTo(first.recommendationId());
    }

    @Test
    void baselineVariantNeverAsksTheLlm() {
        givenUser(PROFILE, CANDIDATES);

        RecommendationListServiceDto list = service(0).getRecommendations(userId);

        assertThat(list.variant()).isEqualTo(ExperimentService.BASELINE_VARIANT);
        assertThat(list.engine()).isEqualTo(Engine.BASELINE);
        verify(llmRankingService, never()).rank(any(), any(), any(), anyInt());
    }

    @Test
    void missingProfileStillGivesAListAndAsksForTheGoal() {
        givenUser(null, CANDIDATES);
        when(llmRankingService.isAvailable()).thenReturn(false);

        RecommendationListServiceDto list = service(100).getRecommendations(userId);

        verify(candidateService).findCandidates(eq(ProfileServiceDto.EMPTY), any());
        assertThat(list.items()).hasSize(3);
        assertThat(list.clarifyingQuestion()).isEqualTo(RecommendationService.NO_GOAL_QUESTION);
    }

    @Test
    void emptyCatalogGivesAnEmptyList() {
        givenUser(PROFILE, List.of());

        RecommendationListServiceDto list = service(100).getRecommendations(userId);

        assertThat(list.items()).isEmpty();
        assertThat(list.engine()).isEqualTo(Engine.BASELINE);
        assertThat(list.clarifyingQuestion()).isEqualTo(RecommendationService.NO_MATCH_QUESTION);
    }
}
