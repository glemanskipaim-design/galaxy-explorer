package dev.galaxyexplorer.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.measurement.MeasurementKey;
import dev.galaxyexplorer.catalog.measurement.MeasurementRepository;
import dev.galaxyexplorer.catalog.measurement.MeasurementRow;
import dev.galaxyexplorer.catalog.measurement.MeasurementWrite;
import dev.galaxyexplorer.catalog.object.CelestialObjectRepository;
import dev.galaxyexplorer.catalog.source.SourceRepository;
import dev.galaxyexplorer.support.Fixtures;

/**
 * Mecanica do repositorio (upsert, limpeza, indice de preferido). Os valores
 * sao os da paralaxe real de Betelgeuse na fixture (6.55 +- 0.83 mas).
 */
class MeasurementRepositoryIntegrationTest extends IntegrationTestBase {

    private static final String URL = "https://simbad.cds.unistra.fr/simbad/sim-id?Ident=%2A%20alf%20Ori";

    @Autowired
    MeasurementRepository measurements;
    @Autowired
    CelestialObjectRepository objects;
    @Autowired
    SourceRepository sources;

    private UUID objectId;
    private short simbad;

    @BeforeEach
    void createObject() {
        objectId = objects.insert("betelgeuse", "Betelgeuse", null, null);
        simbad = sources.getByCode(SourceCode.SIMBAD).id();
    }

    private static MeasurementWrite parallax(String dataset) {
        return MeasurementWrite.builder("parallax", Epistemic.OBSERVED, dataset, URL, Fixtures.CAPTURED_AT)
                .value(6.55)
                .errors(0.83, 0.83)
                .original("6.55", "mas")
                .originalError("0.83")
                .build();
    }

    private static MeasurementWrite computedDistance(long fromId) {
        return MeasurementWrite.builder("distance", Epistemic.COMPUTED_BY_US, "computed:inverse_parallax", URL,
                        Fixtures.CAPTURED_AT)
                .value(1000.0 / 6.55)
                .errors(1000.0 / 6.55 - 1000.0 / 7.38, 1000.0 / 5.72 - 1000.0 / 6.55)
                .original(Double.toString(1000.0 / 6.55), "pc")
                .method("1/plx")
                .derivedFrom(List.of(fromId))
                .build();
    }

    @Test
    void upsertTwiceIsOneRowWithSameId() {
        long first = measurements.upsert(objectId, simbad, parallax("basic"));
        long second = measurements.upsert(objectId, simbad, parallax("basic"));
        assertThat(second).isEqualTo(first);
        assertThat(measurements.findByObject(objectId)).singleElement()
                .satisfies(m -> assertThat(m.originalError()).isEqualTo("0.83"));
    }

    @Test
    void derivedFromArrayRoundTrips() {
        long plx = measurements.upsert(objectId, simbad, parallax("basic"));
        measurements.upsert(objectId, simbad, computedDistance(plx));
        MeasurementRow d = measurements.findByObjectAndProperty(objectId, "distance").get(0);
        assertThat(d.derivedFrom()).containsExactly(plx);
        assertThat(d.epistemic()).isEqualTo("computed_by_us");
    }

    @Test
    void deleteStaleKeepsComputedAndKeptKeys() {
        long plx = measurements.upsert(objectId, simbad, parallax("basic"));
        measurements.upsert(objectId, simbad, computedDistance(plx));
        measurements.upsert(objectId, simbad, MeasurementWrite.builder("spectral_type", Epistemic.OBSERVED, "basic",
                        URL, Fixtures.CAPTURED_AT)
                .text("M1-M2Ia-Iab")
                .original("M1-M2Ia-Iab", null)
                .build());

        int deleted = measurements.deleteStale(objectId, simbad, Set.of(new MeasurementKey("parallax", "", simbad,
                "basic")));

        assertThat(deleted).isEqualTo(1);
        assertThat(measurements.findByObject(objectId)).extracting(MeasurementRow::propertyCode)
                .containsExactlyInAnyOrder("parallax", "distance");
    }

    @Test
    void onePreferredPerPropertyIsEnforcedByDatabase() {
        measurements.upsert(objectId, simbad, parallax("basic"));
        measurements.upsert(objectId, simbad, parallax("other-dataset"));

        assertThatThrownBy(() -> jdbc.sql("UPDATE measurement SET is_preferred = true WHERE object_id = :id")
                .param("id", objectId).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void applyPreferredSwitchesWithoutViolatingIndex() {
        long a = measurements.upsert(objectId, simbad, parallax("basic"));
        long b = measurements.upsert(objectId, simbad, parallax("other-dataset"));
        measurements.applyPreferred(objectId, Set.of(a));
        measurements.applyPreferred(objectId, Set.of(b));
        assertThat(measurements.findByObject(objectId)).filteredOn(MeasurementRow::preferred)
                .extracting(MeasurementRow::id).containsExactly(b);
    }
}
