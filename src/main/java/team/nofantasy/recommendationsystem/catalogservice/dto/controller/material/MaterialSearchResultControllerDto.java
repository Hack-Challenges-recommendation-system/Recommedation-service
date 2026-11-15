package team.nofantasy.recommendationsystem.catalogservice.dto.controller.material;

public record MaterialSearchResultControllerDto(
        MaterialControllerDto material,
        double score
) {
}
