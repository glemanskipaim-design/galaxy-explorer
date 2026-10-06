package dev.galaxyexplorer.catalog.measurement;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PropertyDefinitionRepository {

    private final JdbcClient jdbc;

    public PropertyDefinitionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PropertyDefinition> findAll() {
        return jdbc.sql("""
                SELECT code, label_pt, canonical_unit, value_kind, display_group
                FROM property_definition
                ORDER BY display_group, code
                """)
                .query((rs, n) -> new PropertyDefinition(rs.getString("code"), rs.getString("label_pt"),
                        rs.getString("canonical_unit"), rs.getString("value_kind"), rs.getString("display_group")))
                .list();
    }
}
