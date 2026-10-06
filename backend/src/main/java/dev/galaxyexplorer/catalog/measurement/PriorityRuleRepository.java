package dev.galaxyexplorer.catalog.measurement;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PriorityRuleRepository {

    private final JdbcClient jdbc;

    public PriorityRuleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PriorityRule> findAll() {
        return jdbc.sql("SELECT property_code, category_id, source_id, rank FROM property_source_priority")
                .query((rs, n) -> new PriorityRule(rs.getString("property_code"), (Integer) rs.getObject("category_id"),
                        rs.getShort("source_id"), rs.getShort("rank")))
                .list();
    }
}
