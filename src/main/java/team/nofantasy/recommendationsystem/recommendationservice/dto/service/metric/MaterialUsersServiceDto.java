package team.nofantasy.recommendationsystem.recommendationservice.dto.service.metric;

/**
 * @param users distinct users who started or completed the material.
 */
public record MaterialUsersServiceDto(
        String materialId,
        long users
) {
}
