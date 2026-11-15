package team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog;

/**
 * @param score full-text rank; materials that match more words of the query score higher.
 */
public record MaterialSearchResultServiceDto(
        MaterialServiceDto material,
        double score
) {
}
