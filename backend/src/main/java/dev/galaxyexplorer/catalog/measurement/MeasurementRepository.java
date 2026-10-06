package dev.galaxyexplorer.catalog.measurement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.galaxyexplorer.catalog.Epistemic;
import dev.galaxyexplorer.catalog.JdbcValues;

/**
 * Acesso a {@code measurement}. Nenhum metodo de escrita aqui mexe em
 * {@code is_preferred}, exceto {@link #applyPreferred} (chamado so pelo
 * {@code PreferenceService}).
 */
@Repository
public class MeasurementRepository {

    private static final String SELECT = """
            SELECT m.*, s.code AS source_code, r.bibcode
            FROM measurement m
            JOIN source s ON s.id = m.source_id
            LEFT JOIN bib_reference r ON r.id = m.reference_id
            """;

    private final JdbcClient jdbc;

    public MeasurementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Upsert idempotente na chave unica; devolve o id (estavel entre execucoes). */
    public long upsert(UUID objectId, short sourceId, MeasurementWrite w) {
        return jdbc.sql("""
                INSERT INTO measurement (object_id, property_code, qualifier, value_num, err_minus, err_plus,
                    limit_flag, value_text, original_value, original_unit, original_error, epistemic, method, derived_from,
                    source_id, dataset, source_url, reference_id, raw_payload_id, retrieved_at)
                VALUES (:objectId, :property, :qualifier, :valueNum, :errMinus, :errPlus,
                    :limitFlag, :valueText, :originalValue, :originalUnit, :originalError, :epistemic, :method, :derivedFrom,
                    :sourceId, :dataset, :sourceUrl, :referenceId, :rawPayloadId, :retrievedAt)
                ON CONFLICT (object_id, property_code, qualifier, source_id, dataset) DO UPDATE SET
                    value_num = EXCLUDED.value_num, err_minus = EXCLUDED.err_minus, err_plus = EXCLUDED.err_plus,
                    limit_flag = EXCLUDED.limit_flag, value_text = EXCLUDED.value_text,
                    original_value = EXCLUDED.original_value, original_unit = EXCLUDED.original_unit,
                    original_error = EXCLUDED.original_error,
                    epistemic = EXCLUDED.epistemic, method = EXCLUDED.method, derived_from = EXCLUDED.derived_from,
                    source_url = EXCLUDED.source_url, reference_id = EXCLUDED.reference_id,
                    raw_payload_id = EXCLUDED.raw_payload_id, retrieved_at = EXCLUDED.retrieved_at
                RETURNING id
                """)
                .param("objectId", objectId)
                .param("property", w.propertyCode())
                .param("qualifier", w.qualifier())
                .param("valueNum", w.valueNum())
                .param("errMinus", w.errMinus())
                .param("errPlus", w.errPlus())
                .param("limitFlag", w.limitFlag())
                .param("valueText", w.valueText())
                .param("originalValue", w.originalValue())
                .param("originalUnit", w.originalUnit())
                .param("originalError", w.originalError())
                .param("epistemic", w.epistemic().dbValue())
                .param("method", w.method())
                .param("derivedFrom", w.derivedFrom().isEmpty() ? null : w.derivedFrom().toArray(new Long[0]))
                .param("sourceId", sourceId)
                .param("dataset", w.dataset())
                .param("sourceUrl", w.sourceUrl())
                .param("referenceId", w.referenceId())
                .param("rawPayloadId", w.rawPayloadId())
                .param("retrievedAt", JdbcValues.timestamptz(w.retrievedAt()))
                .query(Long.class)
                .single();
    }

    /**
     * Apaga as medicoes desta fonte para o objeto, exceto {@code computed_by_us}
     * e as chaves em {@code keep} — valor que sumiu da fonte nao fica fantasma.
     */
    public int deleteStale(UUID objectId, short sourceId, Set<MeasurementKey> keep) {
        List<Object[]> tuples = keep.stream()
                .filter(k -> k.sourceId() == sourceId)
                .map(k -> new Object[] {k.propertyCode(), k.qualifier(), k.dataset()})
                .toList();
        String base = """
                DELETE FROM measurement
                WHERE object_id = :objectId AND source_id = :sourceId AND epistemic <> :computed
                """;
        JdbcClient.StatementSpec spec = tuples.isEmpty()
                ? jdbc.sql(base)
                : jdbc.sql(base + " AND (property_code, qualifier, dataset) NOT IN (:keep)").param("keep", tuples);
        return spec.param("objectId", objectId)
                .param("sourceId", sourceId)
                .param("computed", Epistemic.COMPUTED_BY_US.dbValue())
                .update();
    }

    public int deleteByKey(UUID objectId, MeasurementKey key) {
        return jdbc.sql("""
                DELETE FROM measurement
                WHERE object_id = :objectId AND property_code = :property AND qualifier = :qualifier
                  AND source_id = :sourceId AND dataset = :dataset
                """)
                .param("objectId", objectId)
                .param("property", key.propertyCode())
                .param("qualifier", key.qualifier())
                .param("sourceId", key.sourceId())
                .param("dataset", key.dataset())
                .update();
    }

    public List<MeasurementRow> findByObject(UUID objectId) {
        return jdbc.sql(SELECT + " WHERE m.object_id = :objectId ORDER BY m.property_code, m.qualifier, m.id")
                .param("objectId", objectId)
                .query((rs, n) -> map(rs))
                .list();
    }

    public List<MeasurementRow> findByObjectAndProperty(UUID objectId, String propertyCode) {
        return jdbc.sql(SELECT + " WHERE m.object_id = :objectId AND m.property_code = :property ORDER BY m.id")
                .param("objectId", objectId)
                .param("property", propertyCode)
                .query((rs, n) -> map(rs))
                .list();
    }

    /**
     * Marca os preferidos do objeto. Primeiro {@code false} em todas, depois
     * {@code true} nas escolhidas — nessa ordem por causa do indice unico
     * parcial {@code one_preferred}. O chamador abre a transacao.
     */
    public void applyPreferred(UUID objectId, Set<Long> preferredIds) {
        jdbc.sql("UPDATE measurement SET is_preferred = false WHERE object_id = :objectId AND is_preferred")
                .param("objectId", objectId)
                .update();
        if (!preferredIds.isEmpty()) {
            jdbc.sql("UPDATE measurement SET is_preferred = true WHERE object_id = :objectId AND id IN (:ids)")
                    .param("objectId", objectId)
                    .param("ids", preferredIds)
                    .update();
        }
    }

    private static MeasurementRow map(ResultSet rs) throws SQLException {
        return new MeasurementRow(rs.getLong("id"), rs.getObject("object_id", UUID.class),
                rs.getString("property_code"), rs.getString("qualifier"), JdbcValues.nullableDouble(rs, "value_num"),
                JdbcValues.nullableDouble(rs, "err_minus"), JdbcValues.nullableDouble(rs, "err_plus"),
                rs.getString("limit_flag"), rs.getString("value_text"), rs.getString("original_value"),
                rs.getString("original_unit"), rs.getString("original_error"), rs.getString("epistemic"), rs.getString("method"),
                JdbcValues.longList(rs, "derived_from"), rs.getShort("source_id"), rs.getString("source_code"),
                rs.getString("dataset"), rs.getString("source_url"), JdbcValues.nullableLong(rs, "reference_id"),
                rs.getString("bibcode"), JdbcValues.nullableLong(rs, "raw_payload_id"),
                JdbcValues.instant(rs, "retrieved_at"), rs.getBoolean("is_preferred"));
    }
}
