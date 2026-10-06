package dev.galaxyexplorer.catalog.position;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.galaxyexplorer.catalog.JdbcValues;

/**
 * Acesso a {@code object_position}. A tabela nao tem UNIQUE no esquema
 * aprovado; a idempotencia vem de DELETE (objeto, fonte) + INSERT na mesma
 * transacao (aberta por quem chama).
 */
@Repository
public class PositionRepository {

    private final JdbcClient jdbc;

    public PositionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @param referenceId pode ser {@code null} */
    public void replaceForSource(UUID objectId, short sourceId, PositionWrite p, Long referenceId) {
        deleteForSource(objectId, sourceId);
        jdbc.sql("""
                INSERT INTO object_position (object_id, ra_deg, dec_deg, frame, epoch, pos_err_mas, source_id,
                                             reference_id, retrieved_at)
                VALUES (:objectId, :ra, :dec, :frame, :epoch, :err, :sourceId, :refId, :retrievedAt)
                """)
                .param("objectId", objectId)
                .param("ra", p.raDeg())
                .param("dec", p.decDeg())
                .param("frame", p.frame())
                .param("epoch", p.epoch())
                .param("err", p.posErrMas())
                .param("sourceId", sourceId)
                .param("refId", referenceId)
                .param("retrievedAt", JdbcValues.timestamptz(p.retrievedAt()))
                .update();
    }

    /** Remove a posicao desta fonte (usada quando a fonte deixou de fornecer posicao valida). */
    public int deleteForSource(UUID objectId, short sourceId) {
        return jdbc.sql("DELETE FROM object_position WHERE object_id = :objectId AND source_id = :sourceId")
                .param("objectId", objectId)
                .param("sourceId", sourceId)
                .update();
    }

    /** Marca como preferida a posicao coletada mais recentemente (com uma fonte so, e a unica). */
    public void recomputePreferred(UUID objectId) {
        jdbc.sql("UPDATE object_position SET is_preferred = false WHERE object_id = :objectId")
                .param("objectId", objectId)
                .update();
        jdbc.sql("""
                UPDATE object_position SET is_preferred = true
                WHERE id = (SELECT id FROM object_position WHERE object_id = :objectId
                            ORDER BY retrieved_at DESC, id DESC LIMIT 1)
                """)
                .param("objectId", objectId)
                .update();
    }

    public Optional<PositionRow> findPreferred(UUID objectId) {
        return jdbc.sql("""
                SELECT p.ra_deg, p.dec_deg, p.frame, p.epoch, s.code AS source_code, r.bibcode, p.retrieved_at
                FROM object_position p
                JOIN source s ON s.id = p.source_id
                LEFT JOIN bib_reference r ON r.id = p.reference_id
                WHERE p.object_id = :objectId AND p.is_preferred
                """)
                .param("objectId", objectId)
                .query((rs, n) -> new PositionRow(rs.getDouble("ra_deg"), rs.getDouble("dec_deg"),
                        rs.getString("frame"), rs.getString("epoch"), rs.getString("source_code"),
                        rs.getString("bibcode"), JdbcValues.instant(rs, "retrieved_at")))
                .optional();
    }
}
