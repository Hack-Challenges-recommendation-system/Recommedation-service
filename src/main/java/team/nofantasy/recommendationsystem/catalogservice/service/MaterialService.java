package team.nofantasy.recommendationsystem.catalogservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.nofantasy.recommendationsystem.catalogservice.dto.service.material.MaterialPageServiceDto;
import team.nofantasy.recommendationsystem.catalogservice.dto.service.material.MaterialSearchResultServiceDto;
import team.nofantasy.recommendationsystem.catalogservice.dto.service.material.MaterialServiceDto;
import team.nofantasy.recommendationsystem.catalogservice.entity.Material;
import team.nofantasy.recommendationsystem.catalogservice.mapper.MaterialMapper;
import team.nofantasy.recommendationsystem.catalogservice.repository.MaterialRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialService {
    private final MaterialRepository materialRepository;
    private final MaterialMapper materialMapper;

    /**
     * Every filter is optional; {@code null} means "any".
     */
    public MaterialPageServiceDto getMaterials(String format, String stage, String sphere, Boolean paid, int page, int size) {
        Page<Material> materials = materialRepository.findFiltered(format, stage, sphere, paid, PageRequest.of(page, size));

        return new MaterialPageServiceDto(
                materials.getContent().stream().map(materialMapper::materialEntityToServiceDto).toList(),
                materials.getNumber(),
                materials.getSize(),
                materials.getTotalElements(),
                materials.getTotalPages()
        );
    }

    /**
     * @throws EntityNotFoundException if there is no material with this id.
     */
    public MaterialServiceDto getMaterial(String id) {
        return materialRepository.findById(id)
                .map(materialMapper::materialEntityToServiceDto)
                .orElseThrow(() -> new EntityNotFoundException("Material is not found; id=" + id));
    }

    /**
     * @return materials in the order of {@code ids}; unknown ids are skipped.
     */
    public List<MaterialServiceDto> getMaterialsByIds(List<String> ids) {
        Map<String, Material> found = materialRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));

        return ids.stream()
                .distinct()
                .filter(found::containsKey)
                .map(id -> materialMapper.materialEntityToServiceDto(found.get(id)))
                .toList();
    }

    /**
     * Topic-match search: materials ranked by how well their title, topics and summary match the text.
     */
    public List<MaterialSearchResultServiceDto> search(String text, String stage, int limit) {
        List<MaterialRepository.SearchHit> hits = materialRepository.search(text, stage, limit);
        Map<String, Material> found = materialRepository.findAllById(
                hits.stream().map(MaterialRepository.SearchHit::getId).toList()
        ).stream().collect(Collectors.toMap(Material::getId, Function.identity()));

        return hits.stream()
                .filter(hit -> found.containsKey(hit.getId()))
                .map(hit -> new MaterialSearchResultServiceDto(
                        materialMapper.materialEntityToServiceDto(found.get(hit.getId())),
                        hit.getScore()
                ))
                .toList();
    }
}
