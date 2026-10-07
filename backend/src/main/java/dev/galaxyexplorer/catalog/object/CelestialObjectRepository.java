package dev.galaxyexplorer.catalog.object;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.galaxyexplorer.catalog.JdbcValues;

@Repository
public class CelestialObjectRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public CelestialObjectRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * @param categoryId    pode ser {@code null}
     * @param categoryBasis pode ser {@code null}
     */
    public UUID insert(String slug, String displayName, Integer categoryId, String categoryBasis) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return jdbc.sql("""
                INSERT INTO celestial_object (slug, display_name, category_id, category_basis, created_at, updated_at)
                VALUES (:slug, :displayName, :categoryId, :basis, :now, :now)
                RETURNING id
                """)
                .param("slug", slug)
                .param("displayName", displayName)
                .param("categoryId", categoryId)
                .param("basis", categoryBasis)
                .param("now", now)
                .query(UUID.class)
                .single();
    }

    /** Atualiza nome e classificacao; o slug nunca muda. */
    public void updateClassification(UUID id, String displayName, Integer categoryId, String categoryBasis) {
        jdbc.sql("""
                UPDATE celestial_object
                SET display_name = :displayName, category_id = :categoryId, category_basis = :basis, updated_at = :now
                WHERE id = :id
                """)
                .param("id", id)
                .param("displayName", displayName)
                .param("categoryId", categoryId)
                .param("basis", categoryBasis)
                .param("now", OffsetDateTime.now(clock))
                .update();
    }

    public boolean slugExists(String slug) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM celestial_object WHERE slug = :slug)")
                .param("slug", slug)
                .query(Boolean.class)
                .single();
    }

    public Optional<ObjectRow> findBySlug(String slug) {
        return jdbc.sql("SELECT * FROM celestial_object WHERE slug = :slug")
                .param("slug", slug)
                .query((rs, n) -> map(rs))
                .optional();
    }

    public Optional<ObjectRow> findById(UUID id) {
        return jdbc.sql("SELECT * FROM celestial_object WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> map(rs))
                .optional();
    }

    public List<ObjectRow> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT * FROM celestial_object WHERE id IN (:ids)")
                .param("ids", ids)
                .query((rs, n) -> map(rs))
                .list();
    }

    /**
     * Objetos cuja categoria esta em {@code categoryIds} (ids ja resolvidos — este repositorio nao
     * conhece a hierarquia), em ordem natural de nome (collation {@code natural_sort}, V7).
     */
    public List<ObjectRow> findByCategoryIds(Collection<Integer> categoryIds, int limit, int offset) {
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT * FROM celestial_object
                WHERE category_id IN (:ids)
                ORDER BY display_name COLLATE natural_sort, slug
                LIMIT :limit OFFSET :offset
                """)
                .param("ids", categoryIds)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, n) -> map(rs))
                .list();
    }

    public long countByCategoryIds(Collection<Integer> categoryIds) {
        if (categoryIds.isEmpty()) {
            return 0;
        }
        return jdbc.sql("SELECT count(*) FROM celestial_object WHERE category_id IN (:ids)")
                .param("ids", categoryIds)
                .query(Long.class)
                .single();
    }

    private static ObjectRow map(ResultSet rs) throws SQLException {
        return new ObjectRow(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("display_name"),
                (Integer) rs.getObject("category_id"), rs.getString("category_basis"),
                rs.getString("existence_status"), JdbcValues.instant(rs, "created_at"), JdbcValues.instant(rs, "updated_at"));
    }
}
