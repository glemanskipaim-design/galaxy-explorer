package dev.galaxyexplorer.catalog.object;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acesso a {@code object_source_type}. Alem do tipo bruto, guarda
 * {@code source_ref} — o id do objeto na fonte —, que e a identidade do objeto
 * entre execucoes: {@code UNIQUE (source_id, source_ref)}.
 */
@Repository
public class ObjectSourceTypeRepository {

    private final JdbcClient jdbc;

    public ObjectSourceTypeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> findObjectIdBySourceRef(short sourceId, String sourceRef) {
        return jdbc.sql("SELECT object_id FROM object_source_type WHERE source_id = :sourceId AND source_ref = :ref")
                .param("sourceId", sourceId)
                .param("ref", sourceRef)
                .query(UUID.class)
                .optional();
    }

    /** @param morphType pode ser {@code null} */
    public void upsert(UUID objectId, short sourceId, String sourceRef, String sourceType, String morphType,
                       long runId) {
        jdbc.sql("""
                INSERT INTO object_source_type (object_id, source_id, source_ref, source_type, morph_type, run_id)
                VALUES (:objectId, :sourceId, :ref, :type, :morph, :runId)
                ON CONFLICT (object_id, source_id) DO UPDATE
                SET source_ref = EXCLUDED.source_ref, source_type = EXCLUDED.source_type,
                    morph_type = EXCLUDED.morph_type, run_id = EXCLUDED.run_id
                """)
                .param("objectId", objectId)
                .param("sourceId", sourceId)
                .param("ref", sourceRef)
                .param("type", sourceType)
                .param("morph", morphType)
                .param("runId", runId)
                .update();
    }

    public List<SourceTypeRow> findByObject(UUID objectId) {
        return jdbc.sql("""
                SELECT s.code, t.source_ref, t.source_type, t.morph_type
                FROM object_source_type t JOIN source s ON s.id = t.source_id
                WHERE t.object_id = :objectId
                ORDER BY s.code
                """)
                .param("objectId", objectId)
                .query((rs, n) -> new SourceTypeRow(rs.getString("code"), rs.getString("source_ref"),
                        rs.getString("source_type"), rs.getString("morph_type")))
                .list();
    }
}
