package team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation;

import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialServiceDto;

/**
 * @param material always from the catalog, never from the LLM, so links and paid content stay as catalog-service gives them.
 * @param position from 0.
 */
public record RecommendationItemServiceDto(
        MaterialServiceDto material,
        String reason,
        String nextStep,
        int position
) {
}
