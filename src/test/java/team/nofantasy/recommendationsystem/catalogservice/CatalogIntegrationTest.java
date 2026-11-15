package team.nofantasy.recommendationsystem.catalogservice;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;
import team.nofantasy.recommendationsystem.catalogservice.repository.MaterialRepository;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The catalog imported from {@code fixtures/catalog.json} into real Postgres, read through the API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class CatalogIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MaterialRepository materialRepository;

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    @Test
    void importsValidRowsAtStartup() {
        assertThat(materialRepository.count()).isEqualTo(4);
    }

    @Test
    void searchUnderstandsRussianWordForms() throws Exception {
        mockMvc.perform(api(get("/api/v1/materials/search")).param("q", "клиенты"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].material.id").value("m-001"))
                .andExpect(jsonPath("$[0].score").isNumber());
    }

    @Test
    void searchMatchesAnyWordOfTheTask() throws Exception {
        mockMvc.perform(api(get("/api/v1/materials/search")).param("q", "где искать клиентов для магазина"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].material.id").value("m-001"));
    }

    @Test
    void searchRanksTitleMatchesFirstAndFiltersByStage() throws Exception {
        mockMvc.perform(api(get("/api/v1/materials/search")).param("q", "продажи"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].material.id").value("m-003"));

        mockMvc.perform(api(get("/api/v1/materials/search")).param("q", "продажи").param("stage", "LAUNCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].material.id").value("m-001"));
    }

    @Test
    void paidMaterialShowsOnlyShortBenefitAndLink() throws Exception {
        String body = mockMvc.perform(api(get("/api/v1/materials/m-003")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true))
                .andExpect(jsonPath("$.url").value("https://example.com/materials/m-003"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String summary = JsonPath.read(body, "$.summary");
        assertThat(summary).hasSizeLessThanOrEqualTo(300).endsWith("…");
    }

    @Test
    void returnsMaterialsByIdsInRequestedOrder() throws Exception {
        mockMvc.perform(api(get("/api/v1/materials/by-ids")).param("ids", "m-004", "unknown", "m-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("m-004"))
                .andExpect(jsonPath("$[1].id").value("m-001"));
    }

    @Test
    void listsMaterialsWithFiltersAndPages() throws Exception {
        mockMvc.perform(api(get("/api/v1/materials")).param("stage", "LAUNCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value("m-001"))
                .andExpect(jsonPath("$.items[1].id").value("m-002"));

        mockMvc.perform(api(get("/api/v1/materials")).param("paid", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value("m-003"));

        mockMvc.perform(api(get("/api/v1/materials")).param("size", "3").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void unknownMaterialIsNotFound() throws Exception {
        mockMvc.perform(api(get("/api/v1/materials/unknown")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidParameters() throws Exception {
        mockMvc.perform(api(get("/api/v1/materials/search")).param("q", " "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api(get("/api/v1/materials/search")).param("q", "продажи").param("limit", "1000"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api(get("/api/v1/materials")).param("size", "0"))
                .andExpect(status().isBadRequest());
    }
}
