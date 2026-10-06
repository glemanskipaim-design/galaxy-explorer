package dev.galaxyexplorer.catalog.identifier;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a {@code object_identifier}. Nao abre transacao: quem orquestra decide a fronteira. */
@Repository
public class IdentifierRepository {

    private static final RowMapper<IdentifierMatch> MATCH = (rs, n) -> new IdentifierMatch(
            rs.getObject("object_id", UUID.class), rs.getString("identifier"), rs.getString("normalized"),
            rs.getDouble("score"));

    private final JdbcClient jdbc;

    public IdentifierRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Grava identificadores de uma fonte. Dois identificadores que normalizam
     * igual no mesmo objeto: fica o primeiro ({@code ON CONFLICT DO NOTHING}).
     * As linhas chegam com {@code normalized} e {@code catalog} ja calculados.
     */
    public void upsertAll(UUID objectId, short sourceId, List<IdentifierRow> rows) {
        for (IdentifierRow row : rows) {
            jdbc.sql("""
                    INSERT INTO object_identifier (object_id, identifier, normalized, catalog, lang, is_common_name, source_id)
                    VALUES (:objectId, :identifier, :normalized, :catalog, :lang, :common, :sourceId)
                    ON CONFLICT (object_id, normalized) DO NOTHING
                    """)
                    .param("objectId", objectId)
                    .param("identifier", row.identifier())
                    .param("normalized", row.normalized())
                    .param("catalog", row.catalog())
                    .param("lang", row.lang())
                    .param("common", row.isCommonName())
                    .param("sourceId", sourceId)
                    .update();
        }
    }

    /**
     * Remove identificadores desta fonte que nao vieram mais na resposta
     * (identificador que sumiu da fonte nao fica fantasma). Aliases curados
     * ({@code source_id NULL}) e de outras fontes nao sao tocados.
     */
    public int deleteStaleForSource(UUID objectId, short sourceId, Collection<String> keepNormalized) {
        if (keepNormalized.isEmpty()) {
            return jdbc.sql("DELETE FROM object_identifier WHERE object_id = :objectId AND source_id = :sourceId")
                    .param("objectId", objectId)
                    .param("sourceId", sourceId)
                    .update();
        }
        return jdbc.sql("""
                DELETE FROM object_identifier
                WHERE object_id = :objectId AND source_id = :sourceId AND normalized NOT IN (:keep)
                """)
                .param("objectId", objectId)
                .param("sourceId", sourceId)
                .param("keep", keepNormalized)
                .update();
    }

    public List<IdentifierMatch> findExact(String normalized, int limit) {
        return jdbc.sql("""
                SELECT object_id, identifier, normalized, 1.0 AS score
                FROM object_identifier
                WHERE normalized = :q
                ORDER BY is_common_name DESC, identifier
                LIMIT :limit
                """)
                .param("q", normalized)
                .param("limit", limit)
                .query(MATCH)
                .list();
    }

    /** Busca por similaridade de trigramas ({@code pg_trgm}), mais similar primeiro. */
    public List<IdentifierMatch> findFuzzy(String normalized, double minSimilarity, int limit) {
        return jdbc.sql("""
                SELECT object_id, identifier, normalized, similarity(normalized, :q) AS score
                FROM object_identifier
                WHERE similarity(normalized, :q) >= :min
                ORDER BY score DESC, identifier
                LIMIT :limit
                """)
                .param("q", normalized)
                .param("min", minSimilarity)
                .param("limit", limit)
                .query(MATCH)
                .list();
    }

    public List<IdentifierRow> findByObject(UUID objectId) {
        return jdbc.sql("""
                SELECT i.identifier, i.normalized, i.catalog, i.lang, i.is_common_name, s.code AS source_code
                FROM object_identifier i
                LEFT JOIN source s ON s.id = i.source_id
                WHERE i.object_id = :objectId
                ORDER BY i.is_common_name DESC, i.identifier
                """)
                .param("objectId", objectId)
                .query((rs, n) -> new IdentifierRow(rs.getString("identifier"), rs.getString("normalized"),
                        rs.getString("catalog"), rs.getString("lang"), rs.getBoolean("is_common_name"),
                        rs.getString("source_code")))
                .list();
    }
}
