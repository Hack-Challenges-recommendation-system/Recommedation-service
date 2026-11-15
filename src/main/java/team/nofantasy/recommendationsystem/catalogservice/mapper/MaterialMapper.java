package team.nofantasy.recommendationsystem.catalogservice.mapper;

import org.mapstruct.Mapper;
import team.nofantasy.recommendationsystem.catalogservice.dto.controller.material.MaterialControllerDto;
import team.nofantasy.recommendationsystem.catalogservice.dto.controller.material.MaterialPageControllerDto;
import team.nofantasy.recommendationsystem.catalogservice.dto.controller.material.MaterialSearchResultControllerDto;
import team.nofantasy.recommendationsystem.catalogservice.dto.service.material.MaterialPageServiceDto;
import team.nofantasy.recommendationsystem.catalogservice.dto.service.material.MaterialSearchResultServiceDto;
import team.nofantasy.recommendationsystem.catalogservice.dto.service.material.MaterialServiceDto;
import team.nofantasy.recommendationsystem.catalogservice.entity.Material;

import java.util.List;

@Mapper
public interface MaterialMapper {
    MaterialControllerDto materialServiceToControllerDto(MaterialServiceDto materialServiceDto);

    List<MaterialControllerDto> materialServiceToControllerDtoList(List<MaterialServiceDto> materialServiceDtoList);

    MaterialPageControllerDto materialPageServiceToControllerDto(MaterialPageServiceDto materialPageServiceDto);

    MaterialSearchResultControllerDto searchResultServiceToControllerDto(MaterialSearchResultServiceDto searchResult);

    List<MaterialSearchResultControllerDto> searchResultServiceToControllerDtoList(
            List<MaterialSearchResultServiceDto> searchResults
    );

    MaterialServiceDto materialEntityToServiceDto(Material material);
}
