package team.nofantasy.recommendationsystem.catalogservice.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import team.nofantasy.recommendationsystem.catalogservice.dto.controller.material.MaterialControllerDto;
import team.nofantasy.recommendationsystem.catalogservice.dto.controller.material.MaterialPageControllerDto;
import team.nofantasy.recommendationsystem.catalogservice.dto.controller.material.MaterialSearchResultControllerDto;
import team.nofantasy.recommendationsystem.catalogservice.mapper.MaterialMapper;
import team.nofantasy.recommendationsystem.catalogservice.service.MaterialService;

import java.util.List;

/**
 * Read-only catalog. Public: needs no signed-in user.
 */
@RestController
@RequestMapping("/materials")
@RequiredArgsConstructor
@Slf4j
public class MaterialController {
    private final MaterialService materialService;
    private final MaterialMapper materialMapper;

    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    public MaterialPageControllerDto getMaterials(
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String sphere,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size
    ) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "GET /materials")
                .log();

        return materialMapper.materialPageServiceToControllerDto(
                materialService.getMaterials(format, stage, sphere, paid, page, size)
        );
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{id}")
    public MaterialControllerDto getMaterial(@PathVariable String id) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "GET /materials/{id}")
                .addKeyValue("id", id)
                .log();

        return materialMapper.materialServiceToControllerDto(materialService.getMaterial(id));
    }

    /**
     * Materials in the requested order; unknown ids are skipped. recommendation-service uses it for candidates.
     */
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/by-ids")
    public List<MaterialControllerDto> getMaterialsByIds(@RequestParam @Size(min = 1, max = 200) List<String> ids) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "GET /materials/by-ids")
                .addKeyValue("count", ids.size())
                .log();

        return materialMapper.materialServiceToControllerDtoList(materialService.getMaterialsByIds(ids));
    }

    /**
     * Topic-match search with Russian stemming. Also the baseline for comparing recommendations.
     */
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/search")
    public List<MaterialSearchResultControllerDto> search(
            @RequestParam @NotBlank @Size(max = 500) String q,
            @RequestParam(required = false) String stage,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "GET /materials/search")
                .log();

        return materialMapper.searchResultServiceToControllerDtoList(materialService.search(q, stage, limit));
    }
}
