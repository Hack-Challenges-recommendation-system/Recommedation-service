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

    @Test
    void topicMatchesComeFirstAndFinishedMaterialsAreLeftOut() {
        when(catalogService.getMaterials()).thenReturn(TestFixtures.catalog());
        when(catalogService.search("найти первых клиентов", 40))
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

        // topic match, then "taken after" m-003, then the user's stage (m-003, m-004), then the rest
        assertThat(ids(candidates)).containsExactly("m-001", "m-008", "m-005", "m-003", "m-004", "m-007");
        assertThat(candidates.get(0).topicMatch()).isEqualTo(0.6);
        assertThat(candidates.get(2).takenAfter()).isEqualTo("Воронка продаж для малого бизнеса");
        assertThat(candidates.get(2).formatMatch()).as("m-005 is a video").isTrue();
        assertThat(candidates.get(5).popularUsers()).isEqualTo(5);
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
    void emptyProfileGetsTheCatalogAndNoSearch() {
        when(catalogService.getMaterials()).thenReturn(TestFixtures.catalog());

        List<CandidateServiceDto> candidates = candidateService(40).findCandidates(ProfileServiceDto.EMPTY, List.of());

        verify(catalogService, never()).search(anyString(), anyInt());
        assertThat(ids(candidates)).hasSize(8);
    }

    @Test
    void keepsAtMostMaxCandidates() {
        when(catalogService.getMaterials()).thenReturn(TestFixtures.catalog());
        when(catalogService.search("найти первых клиентов", 3))
                .thenReturn(List.of(found("m-001", 0.6), found("m-008", 0.4), found("m-005", 0.2), found("m-003", 0.1)));

        List<CandidateServiceDto> candidates = candidateService(3).findCandidates(PROFILE, List.of());

        assertThat(ids(candidates)).containsExactly("m-001", "m-008", "m-005");
    }
}
