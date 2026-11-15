package team.nofantasy.recommendationsystem.recommendationservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.nofantasy.recommendationsystem.recommendationservice.dto.controller.recommendation.RecommendationListControllerDto;
import team.nofantasy.recommendationsystem.recommendationservice.mapper.RecommendationMapper;
import team.nofantasy.recommendationsystem.recommendationservice.service.RecommendationService;

import java.util.UUID;

/**
 * Recommendations of the signed-in user. The API gateway puts the user id from the access token into
 * {@code X-User-Id}.
 */
@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {
    private final RecommendationService recommendationService;
    private final RecommendationMapper recommendationMapper;

    /**
     * The personal list of materials. When the LLM can't answer, the list is the topic-match baseline with
     * {@code engine: baseline}; only a catalog outage gives 503.
     */
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me")
    public RecommendationListControllerDto getMyRecommendations(@RequestHeader("X-User-Id") UUID userId) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "GET /recommendations/me")
                .addKeyValue("userId", userId)
                .log();

        return recommendationMapper.recommendationListServiceToControllerDto(
                recommendationService.getRecommendations(userId)
        );
    }
}
