package dev.galaxyexplorer.catalog.category;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryRepository {

    private static final RowMapper<CategoryRow> MAPPER = (rs, n) -> new CategoryRow(rs.getInt("id"),
            rs.getString("code"), (Integer) rs.getObject("parent_id"), rs.getString("name_pt"),
            rs.getString("name_en"));

    private final JdbcClient jdbc;

    public CategoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<TypeMappingRule> findRules(short sourceId) {
        return jdbc.sql("""
                SELECT m.id, m.source_type, m.morph_pattern, m.category_id, c.code AS category_code, m.priority
                FROM source_type_mapping m
                JOIN object_category c ON c.id = m.category_id
                WHERE m.source_id = :sourceId
                ORDER BY m.id
                """)
                .param("sourceId", sourceId)
                .query((rs, n) -> new TypeMappingRule(rs.getInt("id"), rs.getString("source_type"),
                        rs.getString("morph_pattern"), rs.getInt("category_id"), rs.getString("category_code"),
                        rs.getShort("priority")))
                .list();
    }

    public Optional<CategoryRow> findByCode(String code) {
        return jdbc.sql("SELECT * FROM object_category WHERE code = :code").param("code", code).query(MAPPER)
                .optional();
    }

    public Optional<CategoryRow> findById(int id) {
        return jdbc.sql("SELECT * FROM object_category WHERE id = :id").param("id", id).query(MAPPER).optional();
    }

    // Toda consulta que percorre a hierarquia object_category.parent_id fica nesta classe.

    /**
     * Todas as categorias raiz (inclusive as sem objetos), cada uma com a contagem de objetos da
     * sua subarvore. Como cada categoria pertence a exatamente uma raiz, as contagens particionam
     * os objetos classificados (nenhum objeto conta duas vezes).
     */
    public List<CategoryCount> findRootsWithCounts() {
        return jdbc.sql("""
                WITH RECURSIVE tree (root_id, id) AS (
                    SELECT id, id FROM object_category WHERE parent_id IS NULL
                    UNION ALL
                    SELECT t.root_id, c.id FROM object_category c JOIN tree t ON c.parent_id = t.id
                )
                SELECT r.id, r.code, r.name_pt, r.name_en, count(o.id) AS object_count
                FROM object_category r
                JOIN tree t ON t.root_id = r.id
                LEFT JOIN celestial_object o ON o.category_id = t.id
                WHERE r.parent_id IS NULL
                GROUP BY r.id, r.code, r.name_pt, r.name_en
                ORDER BY r.name_pt
                """)
                .query((rs, n) -> new CategoryCount(rs.getInt("id"), rs.getString("code"), rs.getString("name_pt"),
                        rs.getString("name_en"), rs.getLong("object_count")))
                .list();
    }

    /** Ids da subarvore de {@code categoryId}, incluindo o proprio. */
    public List<Integer> findSubtreeIds(int categoryId) {
        return jdbc.sql("""
                WITH RECURSIVE tree (id) AS (
                    SELECT id FROM object_category WHERE id = :id
                    UNION ALL
                    SELECT c.id FROM object_category c JOIN tree t ON c.parent_id = t.id
                )
                SELECT id FROM tree
                """)
                .param("id", categoryId)
                .query(Integer.class)
                .list();
    }
}
