package team.nofantasy.recommendationsystem.catalogservice.dto.service.material;

public record MaterialSearchResultServiceDto(
        MaterialServiceDto material,
        double score
) {
}
