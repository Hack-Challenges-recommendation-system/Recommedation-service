package team.nofantasy.recommendationsystem.catalogservice.dto.service.material;

import java.util.List;

public record MaterialPageServiceDto(
        List<MaterialServiceDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
