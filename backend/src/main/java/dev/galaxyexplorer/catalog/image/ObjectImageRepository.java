package dev.galaxyexplorer.catalog.image;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.galaxyexplorer.catalog.JdbcValues;

/**
 * Acesso a {@code object_image}. Nenhum metodo de escrita mexe em
 * {@code is_hero}/{@code gallery_rank}, exceto {@link #applyRanking} (chamado
 * por quem aplica a {@link HeroPolicy}, dentro da transacao do objeto).
 */
@Repository
public class ObjectImageRepository {

    private static final String SELECT = """
            SELECT i.*, s.code AS source_code, s.acknowledgment AS source_acknowledgment, l.name AS license_name
            FROM object_image i
            JOIN source s ON s.id = i.source_id
            JOIN image_license l ON l.code = i.license_code
            """;

    private final JdbcClient jdbc;

    public ObjectImageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Upsert idempotente em {@code (object_id, source_id, source_image_id)}; nao toca hero/rank. */
    public long upsert(UUID objectId, short sourceId, ObjectImageWrite w) {
        return jdbc.sql("""
                INSERT INTO object_image (object_id, kind, image_url, thumb_url, page_url, provider, credit_verbatim,
                    mission, wavelength, license_code, license_url, is_hero, curated_by, retrieved_at,
                    source_id, source_image_id, run_id, metadata_payload_id, download_payload_id, provider_short,
                    title, instrument, rights_text, terms_url, release_date, center_ra_deg, center_dec_deg, fov_deg,
                    fov_basis, coverage_ratio, separation_arcmin, derived_from_measurement_id, match_basis,
                    curated_hero, storage_key, content_sha256, width_px, height_px, byte_size, thumb_storage_key,
                    thumb_sha256, thumb_width_px, thumb_height_px, updated_at)
                VALUES (:objectId, :kind, :imageUrl, NULL, :pageUrl, :provider, :credit,
                    :mission, :wavelength, :licenseCode, :licenseUrl, false, :curatedBy, :retrievedAt,
                    :sourceId, :sourceImageId, :runId, :metadataPayloadId, :downloadPayloadId, :providerShort,
                    :title, :instrument, :rightsText, :termsUrl, :releaseDate, :ra, :dec, :fov,
                    :fovBasis, :coverage, :separation, :measurementId, :matchBasis,
                    :curatedHero, :storageKey, :sha, :width, :height, :byteSize, :thumbKey,
                    :thumbSha, :thumbWidth, :thumbHeight, :retrievedAt)
                ON CONFLICT (object_id, source_id, source_image_id) DO UPDATE SET
                    kind = EXCLUDED.kind, image_url = EXCLUDED.image_url, page_url = EXCLUDED.page_url,
                    provider = EXCLUDED.provider, credit_verbatim = EXCLUDED.credit_verbatim,
                    mission = EXCLUDED.mission, wavelength = EXCLUDED.wavelength,
                    license_code = EXCLUDED.license_code, license_url = EXCLUDED.license_url,
                    curated_by = EXCLUDED.curated_by, retrieved_at = EXCLUDED.retrieved_at,
                    run_id = EXCLUDED.run_id, metadata_payload_id = EXCLUDED.metadata_payload_id,
                    download_payload_id = EXCLUDED.download_payload_id, provider_short = EXCLUDED.provider_short,
                    title = EXCLUDED.title, instrument = EXCLUDED.instrument, rights_text = EXCLUDED.rights_text,
                    terms_url = EXCLUDED.terms_url, release_date = EXCLUDED.release_date,
                    center_ra_deg = EXCLUDED.center_ra_deg, center_dec_deg = EXCLUDED.center_dec_deg,
                    fov_deg = EXCLUDED.fov_deg, fov_basis = EXCLUDED.fov_basis,
                    coverage_ratio = EXCLUDED.coverage_ratio, separation_arcmin = EXCLUDED.separation_arcmin,
                    derived_from_measurement_id = EXCLUDED.derived_from_measurement_id,
                    match_basis = EXCLUDED.match_basis, curated_hero = EXCLUDED.curated_hero,
                    storage_key = EXCLUDED.storage_key, content_sha256 = EXCLUDED.content_sha256,
                    width_px = EXCLUDED.width_px, height_px = EXCLUDED.height_px, byte_size = EXCLUDED.byte_size,
                    thumb_storage_key = EXCLUDED.thumb_storage_key, thumb_sha256 = EXCLUDED.thumb_sha256,
                    thumb_width_px = EXCLUDED.thumb_width_px, thumb_height_px = EXCLUDED.thumb_height_px,
                    updated_at = EXCLUDED.updated_at
                RETURNING id
                """)
                .param("objectId", objectId)
                .param("kind", w.kind().dbValue())
                .param("imageUrl", w.imageUrl())
                .param("pageUrl", w.pageUrl())
                .param("provider", w.provider())
                .param("credit", w.creditVerbatim())
                .param("mission", w.mission())
                .param("wavelength", w.wavelength())
                .param("licenseCode", w.licenseCode())
                .param("licenseUrl", w.licenseUrl())
                .param("curatedBy", w.curatedBy())
                .param("retrievedAt", JdbcValues.timestamptz(w.retrievedAt()))
                .param("sourceId", sourceId)
                .param("sourceImageId", w.sourceImageId())
                .param("runId", w.runId())
                .param("metadataPayloadId", w.metadataPayloadId())
                .param("downloadPayloadId", w.downloadPayloadId())
                .param("providerShort", w.providerShort())
                .param("title", w.title())
                .param("instrument", w.instrument())
                .param("rightsText", w.rightsText())
                .param("termsUrl", w.termsUrl())
                .param("releaseDate", w.releaseDate() == null ? null : Date.valueOf(w.releaseDate()))
                .param("ra", w.centerRaDeg())
                .param("dec", w.centerDecDeg())
                .param("fov", w.fovDeg())
                .param("fovBasis", w.fovBasis())
                .param("coverage", w.coverageRatio())
                .param("separation", w.separationArcmin())
                .param("measurementId", w.derivedFromMeasurementId())
                .param("matchBasis", w.matchBasis())
                .param("curatedHero", w.curatedHero())
                .param("storageKey", w.display().storageKey())
                .param("sha", w.display().sha256())
                .param("width", w.widthPx())
                .param("height", w.heightPx())
                .param("byteSize", w.display().byteSize())
                .param("thumbKey", w.thumb().storageKey())
                .param("thumbSha", w.thumb().sha256())
                .param("thumbWidth", w.thumbWidthPx())
                .param("thumbHeight", w.thumbHeightPx())
                .query(Long.class)
                .single();
    }

    /**
     * Remove as imagens desta fonte e deste {@code curated_by} que nao estao em {@code keepIds}: imagem
     * que a selecao deixou de aceitar nao fica fantasma. Uma rodada automatica nunca apaga linha manual
     * (e vice-versa), porque o filtro inclui {@code curated_by}.
     */
    public int deleteForSourceExcept(UUID objectId, short sourceId, Set<String> keepIds, String curatedBy) {
        String base = """
                DELETE FROM object_image
                WHERE object_id = :objectId AND source_id = :sourceId AND curated_by = :curatedBy
                """;
        JdbcClient.StatementSpec spec = keepIds.isEmpty() ? jdbc.sql(base)
                : jdbc.sql(base + " AND source_image_id NOT IN (:keep)").param("keep", keepIds);
        return spec.param("objectId", objectId)
                .param("sourceId", sourceId)
                .param("curatedBy", curatedBy)
                .update();
    }

    /** Ordenado como exibido: hero primeiro, depois {@code gallery_rank}. */
    public List<ObjectImageRow> findByObject(UUID objectId) {
        return jdbc.sql(SELECT + " WHERE i.object_id = :objectId ORDER BY i.is_hero DESC, i.gallery_rank, i.id")
                .param("objectId", objectId)
                .query((rs, n) -> map(rs))
                .list();
    }

    /** {@code source_image_id} ja gravados para o objeto nesta fonte. */
    public Set<String> findSourceImageIds(UUID objectId, short sourceId) {
        return Set.copyOf(jdbc.sql("""
                SELECT source_image_id FROM object_image WHERE object_id = :objectId AND source_id = :sourceId
                """)
                .param("objectId", objectId)
                .param("sourceId", sourceId)
                .query(String.class)
                .list());
    }

    /** Miniatura do hero de cada objeto pedido, numa consulta so. Objeto sem hero nao aparece no mapa. */
    public Map<UUID, HeroThumb> findHeroThumbs(Collection<UUID> objectIds) {
        Map<UUID, HeroThumb> out = new LinkedHashMap<>();
        if (objectIds.isEmpty()) {
            return out;
        }
        jdbc.sql("""
                SELECT object_id, thumb_sha256, thumb_width_px, thumb_height_px, kind, provider_short, license_code
                FROM object_image
                WHERE is_hero AND object_id IN (:ids)
                """)
                .param("ids", objectIds)
                .query((rs, n) -> new HeroThumb(rs.getObject("object_id", UUID.class), rs.getString("thumb_sha256"),
                        rs.getInt("thumb_width_px"), rs.getInt("thumb_height_px"), ImageKind.fromDb(rs.getString("kind")),
                        rs.getString("provider_short"), rs.getString("license_code")))
                .list()
                .forEach(t -> out.put(t.objectId(), t));
        return out;
    }

    /**
     * Grava a ordem da galeria e o hero. Primeiro zera o hero, depois marca — nessa ordem por causa do
     * indice unico parcial {@code one_hero_image}. O chamador abre a transacao.
     *
     * @param orderedIds ids na ordem de exibicao (indice = {@code gallery_rank})
     * @param heroId     pode ser {@code null} (nenhuma imagem pode ser hero)
     */
    public void applyRanking(UUID objectId, List<Long> orderedIds, Long heroId) {
        jdbc.sql("UPDATE object_image SET is_hero = false WHERE object_id = :objectId AND is_hero")
                .param("objectId", objectId)
                .update();
        for (int rank = 0; rank < orderedIds.size(); rank++) {
            jdbc.sql("UPDATE object_image SET gallery_rank = :rank WHERE id = :id AND object_id = :objectId")
                    .param("rank", rank)
                    .param("id", orderedIds.get(rank))
                    .param("objectId", objectId)
                    .update();
        }
        if (heroId != null) {
            jdbc.sql("UPDATE object_image SET is_hero = true WHERE id = :id AND object_id = :objectId")
                    .param("id", heroId)
                    .param("objectId", objectId)
                    .update();
        }
    }

    /** Arquivo de exibicao OU miniatura com este sha. Vazio = o banco nao conhece este conteudo. */
    public Optional<String> findStorageKeyBySha(String sha256) {
        return jdbc.sql("""
                SELECT storage_key FROM object_image WHERE content_sha256 = :sha
                UNION ALL
                SELECT thumb_storage_key FROM object_image WHERE thumb_sha256 = :sha
                LIMIT 1
                """)
                .param("sha", sha256)
                .query(String.class)
                .optional();
    }

    private static ObjectImageRow map(ResultSet rs) throws SQLException {
        Date release = rs.getDate("release_date");
        LocalDate releaseDate = release == null ? null : release.toLocalDate();
        return new ObjectImageRow(rs.getLong("id"), rs.getObject("object_id", UUID.class),
                ImageKind.fromDb(rs.getString("kind")), rs.getBoolean("is_hero"), rs.getString("source_code"),
                rs.getString("source_acknowledgment"), rs.getString("source_image_id"), rs.getString("image_url"),
                rs.getString("page_url"), rs.getString("provider"), rs.getString("provider_short"),
                rs.getString("credit_verbatim"), rs.getString("mission"), rs.getString("instrument"),
                rs.getString("wavelength"), rs.getString("title"), rs.getString("rights_text"),
                rs.getString("license_code"), rs.getString("license_name"), rs.getString("license_url"),
                rs.getString("terms_url"), releaseDate, JdbcValues.nullableDouble(rs, "center_ra_deg"),
                JdbcValues.nullableDouble(rs, "center_dec_deg"), JdbcValues.nullableDouble(rs, "fov_deg"),
                rs.getString("fov_basis"), JdbcValues.nullableDouble(rs, "coverage_ratio"),
                JdbcValues.nullableDouble(rs, "separation_arcmin"), rs.getString("match_basis"),
                rs.getString("curated_by"), rs.getBoolean("curated_hero"), rs.getString("content_sha256"),
                rs.getInt("width_px"), rs.getInt("height_px"), rs.getString("thumb_sha256"),
                rs.getInt("thumb_width_px"), rs.getInt("thumb_height_px"), rs.getInt("gallery_rank"),
                JdbcValues.instant(rs, "retrieved_at"));
    }
}
