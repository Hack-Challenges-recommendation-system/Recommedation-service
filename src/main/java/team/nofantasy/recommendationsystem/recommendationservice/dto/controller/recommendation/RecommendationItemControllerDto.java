package team.nofantasy.recommendationsystem.recommendationservice.dto.controller.recommendation;

import team.nofantasy.recommendationsystem.recommendationservice.dto.controller.material.MaterialControllerDto;

/**
 * @param reason why the material was picked for this user.
 * @param nextStep what to do with it.
 * @param position from 0; the frontend sends it back with the events of the item.
 */
public record RecommendationItemControllerDto(
        MaterialControllerDto material,
        String reason,
        String nextStep,
        int position
) {
}
