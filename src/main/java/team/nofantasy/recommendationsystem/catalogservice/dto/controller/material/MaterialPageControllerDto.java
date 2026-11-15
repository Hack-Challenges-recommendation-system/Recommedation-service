package team.nofantasy.recommendationsystem.catalogservice.dto.controller.material;

import java.util.List;

public record MaterialPageControllerDto(
        List<MaterialControllerDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
