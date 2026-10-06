package dev.galaxyexplorer.catalog.source;

import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.galaxyexplorer.catalog.SourceCode;

@Repository
public class SourceRepository {

    private static final RowMapper<SourceRow> MAPPER = (rs, n) -> new SourceRow(rs.getShort("id"),
            rs.getString("code"), rs.getString("name"), rs.getString("homepage_url"), rs.getString("terms_url"),
            rs.getString("acknowledgment"));

    private final JdbcClient jdbc;

    public SourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @throws IllegalStateException se a fonte nao existe (migracao de dados de referencia nao rodou) */
    public SourceRow getByCode(SourceCode code) {
        return jdbc.sql("SELECT * FROM source WHERE code = :code")
                .param("code", code.name())
                .query(MAPPER)
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "fonte " + code + " ausente na tabela source — V3__reference_data.sql nao foi aplicada?"));
    }

    public List<SourceRow> findByCodes(Collection<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT * FROM source WHERE code IN (:codes) ORDER BY code")
                .param("codes", codes)
                .query(MAPPER)
                .list();
    }

    public List<SourceRow> findByIds(Collection<Short> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT * FROM source WHERE id IN (:ids) ORDER BY code")
                .param("ids", ids)
                .query(MAPPER)
                .list();
    }
}
