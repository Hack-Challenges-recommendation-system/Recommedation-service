package team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog;

import java.util.List;

public record MaterialPageServiceDto(
        List<MaterialServiceDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
