package dev.galaxyexplorer.catalog.derived;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.galaxyexplorer.catalog.Epistemic;
import dev.galaxyexplorer.catalog.measurement.MeasurementKey;
import dev.galaxyexplorer.catalog.measurement.MeasurementRepository;
import dev.galaxyexplorer.catalog.measurement.MeasurementRow;
import dev.galaxyexplorer.catalog.measurement.MeasurementWrite;

/**
 * Valores {@code computed_by_us}. No Bloco 1: distancia = 1/paralaxe, uma por
 * paralaxe (uma por fonte). A proveniencia do calculo e a do dado de entrada
 * (fonte, URL, referencia, payload e data herdados da paralaxe), e
 * {@code derived_from} aponta para o id da paralaxe usada.
 *
 * <p>Nao abre transacao: roda dentro da transacao do chamador.
 */
@Service
public class DerivedValueService {

    public static final String DISTANCE_DATASET = "computed:inverse_parallax";
    public static final String DISTANCE_METHOD = "1/plx";

    private final MeasurementRepository measurements;

    public DerivedValueService(MeasurementRepository measurements) {
        this.measurements = measurements;
    }

    public void recompute(UUID objectId) {
        Set<Short> computedSources = new HashSet<>();
        for (MeasurementRow parallax : measurements.findByObjectAndProperty(objectId, "parallax")) {
            if (parallax.valueNum() == null) {
                continue;
            }
            DistanceComputation result = DistanceFromParallax.compute(parallax.valueNum(), parallax.errMinus());
            if (result instanceof DistanceComputation.Computed c) {
                measurements.upsert(objectId, parallax.sourceId(), distanceFrom(parallax, c));
                computedSources.add(parallax.sourceId());
            }
        }
        // Paralaxe que piorou (S/N < 5) ou sumiu num refresh: a distancia calculada antiga some.
        List<MeasurementRow> stale = measurements.findByObjectAndProperty(objectId, "distance").stream()
                .filter(m -> DISTANCE_DATASET.equals(m.dataset()))
                .filter(m -> !computedSources.contains(m.sourceId()))
                .toList();
        for (MeasurementRow row : stale) {
            measurements.deleteByKey(objectId, new MeasurementKey("distance", "", row.sourceId(), DISTANCE_DATASET));
        }
    }

    private static MeasurementWrite distanceFrom(MeasurementRow parallax, DistanceComputation.Computed c) {
        // original_value de um computed_by_us e a representacao do proprio calculo (nao ha "original" na fonte)
        return MeasurementWrite.builder("distance", Epistemic.COMPUTED_BY_US, DISTANCE_DATASET, parallax.sourceUrl(),
                        parallax.retrievedAt())
                .value(c.valuePc())
                .errors(c.errMinusPc(), c.errPlusPc())
                .original(Double.toString(c.valuePc()), "pc")
                .method(DISTANCE_METHOD)
                .derivedFrom(List.of(parallax.id()))
                .bibcode(parallax.bibcode())
                .referenceId(parallax.referenceId())
                .rawPayloadId(parallax.rawPayloadId())
                .build();
    }
}
