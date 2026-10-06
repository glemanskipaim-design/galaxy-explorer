package dev.galaxyexplorer.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.source.SourceRepository;
import dev.galaxyexplorer.catalog.write.CatalogWriter;
import dev.galaxyexplorer.catalog.write.ObjectWrite;
import dev.galaxyexplorer.catalog.write.WriteResult;
import dev.galaxyexplorer.ingestion.connector.FetchedBatch;
import dev.galaxyexplorer.ingestion.connector.NormalizationOutcome;
import dev.galaxyexplorer.ingestion.connector.NormalizedObject;
import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.simbad.SimbadConnector;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRunRepository;
import dev.galaxyexplorer.ingestion.pipeline.NormalizedToCatalog;
import dev.galaxyexplorer.ingestion.pipeline.RawPayloadRepository;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;
import dev.galaxyexplorer.ingestion.pipeline.StoredPayload;
import dev.galaxyexplorer.support.Samples;

/** CatalogWriter sobre o M31 real normalizado das fixtures. */
class CatalogWriterIntegrationTest extends IntegrationTestBase {

    @Autowired
    CatalogWriter writer;
    @Autowired
    SimbadConnector connector;
    @Autowired
    IngestionRunRepository runs;
    @Autowired
    RawPayloadRepository rawPayloads;
    @Autowired
    SourceRepository sources;
    @Autowired
    TransactionTemplate tx;

    private ObjectWrite m31;

    @BeforeEach
    void normalizeM31() {
        long runId = runs.open(sources.getByCode(SourceCode.SIMBAD).id(), RunTrigger.MANUAL, "test");
        FetchedBatch batch = connector.fetch(connector.resolve(Samples.QUERIES).resolved());
        Map<String, StoredPayload> stored = new HashMap<>();
        for (RawPayload p : batch.payloadsByDataset().values()) {
            stored.put(p.dataset(), new StoredPayload(rawPayloads.insert(runId, p), p.retrievedAt()));
        }
        NormalizedObject object = connector.normalize(batch).stream()
                .map(o -> ((NormalizationOutcome.Accepted) o).object())
                .filter(o -> o.ref().sourceInternalId() == 1575544L)
                .findFirst().orElseThrow();
        m31 = NormalizedToCatalog.toWrite(object, stored, runId);
    }

    private WriteResult write(ObjectWrite w) {
        return tx.execute(s -> writer.write(w));
    }

    @Test
    void rewritingSameSourceObjectKeepsIdentitySlugAndCounts() {
        WriteResult first = write(m31);
        int measurements = count("measurement");
        int identifiers = count("object_identifier");
        WriteResult second = write(m31);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.objectId()).isEqualTo(first.objectId());
        assertThat(second.slug()).isEqualTo("messier-31").isEqualTo(first.slug());
        assertThat(count("celestial_object")).isEqualTo(1);
        assertThat(count("measurement")).isEqualTo(measurements);
        assertThat(count("object_identifier")).isEqualTo(identifiers);
        assertThat(count("object_position")).isEqualTo(1);
    }

    @Test
    void identityIsSourceRefSoAnotherRefIsAnotherObjectWithSuffixedSlug() {
        write(m31);
        ObjectWrite otherRef = new ObjectWrite(m31.source(), "outro-oid-de-teste", m31.sourceMainId(),
                m31.sourceType(), m31.morphType(), m31.identifiers(), m31.position(), m31.measurements(),
                m31.runId());

        WriteResult second = write(otherRef);

        assertThat(second.created()).isTrue();
        assertThat(second.slug()).isEqualTo("messier-31-2");
        assertThat(count("celestial_object")).isEqualTo(2);
    }

    @Test
    void classificationAndBasisPersisted() {
        WriteResult r = write(m31);
        Map<String, Object> row = jdbc.sql("""
                SELECT o.display_name, c.code, o.category_basis, t.source_ref, t.source_type, t.morph_type
                FROM celestial_object o JOIN object_category c ON c.id = o.category_id
                JOIN object_source_type t ON t.object_id = o.id WHERE o.id = :id
                """).param("id", r.objectId()).query().singleRow();
        assertThat(row.get("display_name")).isEqualTo("M 31");
        assertThat(row.get("code")).isEqualTo("galaxy_spiral");
        assertThat((String) row.get("category_basis")).contains("otype=AGN").contains("morph_type=SA(s)b");
        assertThat(row.get("source_ref")).isEqualTo("1575544");
    }
}
