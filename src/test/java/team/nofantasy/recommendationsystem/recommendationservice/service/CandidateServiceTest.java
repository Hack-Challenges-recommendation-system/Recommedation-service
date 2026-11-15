package team.nofantasy.recommendationsystem.recommendationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.nofantasy.recommendationsystem.recommendationservice.TestFixtures;
import team.nofantasy.recommendationsystem.recommendationservice.client.MetricClient;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialSearchResultServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.metric.EventServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.metric.MaterialUsersServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.profile.ProfileServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.CandidateServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.CandidateServiceDto.HistorySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {
    private static final ProfileServiceDto PROFILE = new ProfileServiceDto(
            "LAUNCH", "Розничная торговля", "NONE", "найти первых клиентов", List.of("VIDEO"), Instant.EPOCH
    );
    /** The goal and the sphere of {@link #PROFILE}. */
    private static final String PROFILE_QUERY = "найти первых клиентов. Розничная торговля";

    @Mock
    private CatalogService catalogService;
    @Mock
    private MetricClient metricClient;

    private CandidateService candidateService(int maxCandidates) {
        return new CandidateService(catalogService, metricClient, TestFixtures.properties(6, maxCandidates, 100));
    }

    private static MaterialSearchResultServiceDto found(String id, double score) {
        return new MaterialSearchResultServiceDto(TestFixtures.material(id), score);
    }

    private static EventServiceDto event(String type, String materialId) {
        return new EventServiceDto(type, materialId, Instant.EPOCH);
    }

    private static List<String> ids(List<CandidateServiceDto> candidates) {
        return candidates.stream().map(candidate -> candidate.material().id()).toList();
    }

    private static CandidateServiceDto candidate(List<CandidateServiceDto> candidates, String id) {
        return candidates.stream().filter(candidate -> candidate.material().id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void topicMatchesComeFirstAndFinishedMaterialsAreLeftOut() {
        when(catalogService.getMaterials()).thenReturn(TestFixtures.catalog());
        when(catalogService.search(PROFILE_QUERY, 40))
                .thenReturn(List.of(found("m-001", 0.6), found("m-008", 0.4), found("m-002", 0.1)));
        when(metricClient.getPopularMaterials(anyInt())).thenReturn(List.of(new MaterialUsersServiceDto("m-007", 5)));
        when(metricClient.getMaterialsTakenNext(eq("m-003"), anyInt()))
                .thenReturn(List.of(new MaterialUsersServiceDto("m-005", 3)));
        when(metricClient.getMaterialsTakenNext(eq("m-002"), anyInt())).thenReturn(List.of());

        List<CandidateServiceDto> candidates = candidateService(40).findCandidates(PROFILE, List.of(
                event("START", "m-003"),
                event("COMPLETE", "m-002"),
                event("DISLIKE", "m-006")
        ));

        // topic match, then "taken after" m-003, then "similar to" completed m-002, then the user's stage, the rest
        assertThat(ids(candidates)).containsExactly("m-001", "m-008", "m-005", "m-004", "m-003", "m-007");
        assertThat(candidates.get(0).topicMatch()).isEqualTo(0.6);
        assertThat(candidates.get(2).takenAfter()).isEqualTo("Воронка продаж для малого бизнеса");
        assertThat(candidates.get(2).formatMatch()).as("m-005 is a video").isTrue();
        assertThat(candidates.get(3).similarTo())
                .as("m-004 shares «финансы» with m-002")
                .isEqualTo(new HistorySource("Налоги для ИП: какой режим выбрать", "COMPLETE"));
        assertThat(candidates.get(5).popularUsers()).isEqualTo(5);
    }

    @Test
    void sphereJoinsTheGoalInTheSearch() {
        assertThat(CandidateService.topicQuery(PROFILE)).isEqualTo(PROFILE_QUERY);
        assertThat(CandidateService.topicQuery(new ProfileServiceDto(
                null, "Общепит", null, "Открыть общепит у дома", List.of(), Instant.EPOCH
        ))).as("the goal already names the sphere").isEqualTo("Открыть общепит у дома");
        assertThat(CandidateService.topicQuery(ProfileServiceDto.EMPTY)).isEmpty();
    }

    @Test
    void withoutGoalTheSphereIsSearched() {
        ProfileServiceDto withoutGoal = new ProfileServiceDto("LAUNCH", "Розничная торговля", null, " ", List.of(), Instant.EPOCH);
        when(catalogService.getMaterials()).thenReturn(TestFixtures.catalog());
        when(catalogService.search("Розничная торговля", 40)).thenReturn(List.of(found("m-003", 0.3)));

        List<CandidateServiceDto> candidates = candidateService(40).findCandidates(withoutGoal, List.of());

        assertThat(ids(candidates).getFirst()).isEqualTo("m-003");
    }

    @Test
    void likedMaterialBringsSimilarOnesEvenWhenTheGoalMatchesMore() {
        when(catalogService.getMaterials()).thenReturn(TestFixtures.catalog());
        when(catalogService.search(PROFILE_QUERY, 5)).thenReturn(List.of(
                found("m-008", 0.6), found("m-003", 0.5), found("m-002", 0.4), found("m-004", 0.3), found("m-007", 0.2)
        ));

        List<CandidateServiceDto> candidates = candidateService(5)
                .findCandidates(PROFILE, List.of(event("LIKE", "m-005")));

        // m-001 shares «маркетинг» with the liked m-005 and keeps its slot; the weakest topic match is left out
        assertThat(ids(candidates)).hasSize(5).contains("m-001").doesNotContain("m-007");
        assertThat(candidate(candidates, "m-001").similarTo())
                .isEqualTo(new HistorySource("Маркетинг в соцсетях без бюджета", "LIKE"));
    }

    @Test
    void latestReactionWins() {
        when(catalogService.getMaterials()).thenReturn(TestFixtures.catalog());

        // newest first: m-006 was disliked, then liked; m-001 was liked, then disliked
        List<CandidateServiceDto> candidates = candidateService(40).findCandidates(ProfileServiceDto.EMPTY, List.of(
                event("LIKE", "m-006"),
                event("DISLIKE", "m-001"),
                event("DISLIKE", "m-006"),
                event("LIKE", "m-001")
        ));

        assertThat(ids(candidates)).contains("m-006").doesNotContain("m-001");
        assertThat(candidate(candidates, "m-007").similarTo())
                .as("m-007 shares «управление» with the liked m-006")
                .isEqualTo(new HistorySource("Как нанять первого сотрудника", "LIKE"));
    }

    @Test
    void emptyProfileGetsAStarterSetOnDifferentTopicsAndNoSearch() {
        when(catalogService.getMaterials()).thenReturn(TestFixtures.catalog());

        List<CandidateServiceDto> candidates = candidateService(40).findCandidates(ProfileServiceDto.EMPTY, List.of());

        verify(catalogService, never()).search(anyString(), anyInt());
        // beginner-friendly and short first, one material per first topic before a second one on the same topic
        assertThat(ids(candidates))
                .containsExactly("m-001", "m-002", "m-004", "m-008", "m-006", "m-007", "m-005", "m-003");
    }

    @Test
    void keepsAtMostMaxCandidates() {
        when(catalogService.getMaterials()).thenReturn(TestFixtures.catalog());
        when(catalogService.search(PROFILE_QUERY, 3))
                .thenReturn(List.of(found("m-001", 0.6), found("m-008", 0.4), found("m-005", 0.2), found("m-003", 0.1)));

        List<CandidateServiceDto> candidates = candidateService(3).findCandidates(PROFILE, List.of());

        assertThat(ids(candidates)).containsExactly("m-001", "m-008", "m-005");
    }
}
