package dev.galaxyexplorer.ingestion.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.galaxyexplorer.catalog.measurement.MeasurementWrite;
import dev.galaxyexplorer.catalog.position.PositionWrite;
import dev.galaxyexplorer.catalog.write.IdentifierWrite;
import dev.galaxyexplorer.catalog.write.ObjectWrite;
import dev.galaxyexplorer.ingestion.connector.NormalizedMeasurement;
import dev.galaxyexplorer.ingestion.connector.NormalizedObject;
import dev.galaxyexplorer.ingestion.connector.NormalizedPosition;

/**
 * Traduz o modelo canonico (ingestao) para a entrada do catalogo (dominio).
 * Puro. Preenche {@code raw_payload_id}/{@code retrieved_at} pelo
 * {@code payloadDataset} de cada valor e {@code source_url} com o link do objeto
 * na fonte.
 */
public final class NormalizedToCatalog {

    private NormalizedToCatalog() {
    }

    public static ObjectWrite toWrite(NormalizedObject o, Map<String, StoredPayload> payloadsByDataset, long runId) {
        List<IdentifierWrite> identifiers = o.identifiers().stream()
                .map(i -> new IdentifierWrite(i.identifier(), i.isCommonName()))
                .toList();
        Optional<PositionWrite> position = o.position().map(p -> toPosition(p, payloadsByDataset));
        List<MeasurementWrite> measurements = o.measurements().stream()
                .map(m -> toMeasurement(m, o.sourceObjectUrl(), payloadsByDataset))
                .toList();
        return new ObjectWrite(o.ref().source(), o.ref().sourceRef(), o.mainId(), o.sourceType(), o.morphType(),
                identifiers, position, measurements, runId);
    }

    private static PositionWrite toPosition(NormalizedPosition p, Map<String, StoredPayload> payloads) {
        StoredPayload stored = payload(payloads, p.payloadDataset());
        return new PositionWrite(p.raDeg(), p.decDeg(), p.frame(), p.epoch(), null, p.bibcode(),
                stored.retrievedAt());
    }

    private static MeasurementWrite toMeasurement(NormalizedMeasurement m, String sourceUrl,
                                                  Map<String, StoredPayload> payloads) {
        StoredPayload stored = payload(payloads, m.payloadDataset());
        return MeasurementWrite.builder(m.propertyCode(), m.epistemic(), m.dataset(), sourceUrl, stored.retrievedAt())
                .qualifier(m.qualifier())
                .value(m.valueNum())
                .errors(m.errMinus(), m.errPlus())
                .limitFlag(m.limitFlag())
                .text(m.valueText())
                .original(m.originalValue(), m.originalUnit())
                .originalError(m.originalError())
                .method(m.method())
                .bibcode(m.bibcode())
                .rawPayloadId(stored.id())
                .build();
    }

    private static StoredPayload payload(Map<String, StoredPayload> payloads, String dataset) {
        StoredPayload stored = payloads.get(dataset);
        if (stored == null) {
            throw new IllegalStateException("valor referencia payload '" + dataset + "' que não foi gravado no lote");
        }
        return stored;
    }
}
