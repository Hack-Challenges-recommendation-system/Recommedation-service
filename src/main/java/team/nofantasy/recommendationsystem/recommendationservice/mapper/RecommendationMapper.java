package team.nofantasy.recommendationsystem.recommendationservice.mapper;

import org.mapstruct.Mapper;
import team.nofantasy.recommendationsystem.recommendationservice.dto.controller.material.MaterialControllerDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.controller.recommendation.RecommendationItemControllerDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.controller.recommendation.RecommendationListControllerDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.RecommendationItemServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.RecommendationListServiceDto;

import java.util.Locale;

/**
 * Recommendations are read-only, so there are only service-to-controller mappings.
 */
@Mapper
public interface RecommendationMapper {
    RecommendationListControllerDto recommendationListServiceToControllerDto(
            RecommendationListServiceDto recommendationListServiceDto
    );

    RecommendationItemControllerDto recommendationItemServiceToControllerDto(
            RecommendationItemServiceDto recommendationItemServiceDto
    );

    MaterialControllerDto materialServiceToControllerDto(MaterialServiceDto materialServiceDto);

    default String engineToValue(RecommendationListServiceDto.Engine engine) {
        return engine == null ? null : engine.name().toLowerCase(Locale.ROOT);
    }
}
