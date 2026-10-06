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
}
