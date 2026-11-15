package team.nofantasy.recommendationsystem.catalogservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import team.nofantasy.recommendationsystem.catalogservice.entity.Material;

import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, String> {
    /**
     * A material found by text search with its rank; higher is more relevant.
     */
    interface SearchHit {
        String getId();

        Double getScore();
    }

    String FILTER = """
            WHERE (CAST(:format AS text) IS NULL OR m.format = CAST(:format AS text))
              AND (CAST(:stage AS text) IS NULL OR CAST(:stage AS text) = ANY(m.business_stages))
              AND (CAST(:sphere AS text) IS NULL OR CAST(:sphere AS text) = ANY(m.spheres))
              AND (CAST(:paid AS boolean) IS NULL OR m.paid = CAST(:paid AS boolean))
            """;

    @Query(
            value = "SELECT m.* FROM material m " + FILTER + " ORDER BY m.id",
            countQuery = "SELECT count(*) FROM material m " + FILTER,
            nativeQuery = true
    )
    Page<Material> findFiltered(
            @Param("format") String format,
            @Param("stage") String stage,
            @Param("sphere") String sphere,
            @Param("paid") Boolean paid,
            Pageable pageable
    );

    /**
     * Russian full-text search that matches any word of the text: materials matching more words rank higher.
     * This is the topic-match baseline that recommendations are compared against.
     */
    @Query(value = """
            SELECT m.id AS id, CAST(ts_rank_cd(m.search_vector, q.query) AS double precision) AS score
            FROM material m,
                 (SELECT replace(plainto_tsquery('russian', :text)::text, '&', '|')::tsquery AS query) q
            WHERE m.search_vector @@ q.query
              AND (CAST(:stage AS text) IS NULL OR CAST(:stage AS text) = ANY(m.business_stages))
            ORDER BY score DESC, m.id
            LIMIT :limit
            """, nativeQuery = true)
    List<SearchHit> search(@Param("text") String text, @Param("stage") String stage, @Param("limit") int limit);
}
