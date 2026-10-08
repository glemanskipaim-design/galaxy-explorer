package dev.galaxyexplorer.catalog.image;

import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.galaxyexplorer.catalog.JdbcValues;

/**
 * {@code hips_survey_snapshot}: o credito/licenca que um survey HiPS declarava
 * quando gravamos recortes dele (literais do arquivo {@code properties}).
 */
@Repository
public class HipsSurveySnapshotRepository {

    private final JdbcClient jdbc;

    public HipsSurveySnapshotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Campos de texto podem ser {@code null} (chave ausente no {@code properties}). */
    public long insert(String hipsId, String propertiesUrl, String obsTitle, String obsCopyright,
                       String obsCopyrightUrl, String hipsLicense, String hipsCopyright, String bibReference,
                       long rawPayloadId,
                       Instant retrievedAt) {
        return jdbc.sql("""
                INSERT INTO hips_survey_snapshot (hips_id, properties_url, obs_title, obs_copyright, obs_copyright_url,
                                                  hips_license, hips_copyright, bib_reference, raw_payload_id,
                                                  retrieved_at)
                VALUES (:hipsId, :url, :title, :copyright, :copyrightUrl, :license, :hipsCopyright, :bib, :payload,
                        :at)
                RETURNING id
                """)
                .param("hipsId", hipsId)
                .param("url", propertiesUrl)
                .param("title", obsTitle)
                .param("copyright", obsCopyright)
                .param("copyrightUrl", obsCopyrightUrl)
                .param("license", hipsLicense)
                .param("hipsCopyright", hipsCopyright)
                .param("bib", bibReference)
                .param("payload", rawPayloadId)
                .param("at", JdbcValues.timestamptz(retrievedAt))
                .query(Long.class)
                .single();
    }
}
