package dev.galaxyexplorer.catalog.measurement;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.galaxyexplorer.catalog.object.CelestialObjectRepository;
import dev.galaxyexplorer.catalog.object.ObjectRow;
import dev.galaxyexplorer.catalog.position.PositionRepository;

/**
 * Unico dono da escrita de {@code is_preferred} (medicoes e posicao). Nao abre
 * transacao: roda dentro da transacao do chamador.
 */
@Service
public class PreferenceService {

    private final MeasurementRepository measurements;
    private final PriorityRuleRepository priorityRules;
    private final CelestialObjectRepository objects;
    private final PositionRepository positions;

    public PreferenceService(MeasurementRepository measurements, PriorityRuleRepository priorityRules,
                             CelestialObjectRepository objects, PositionRepository positions) {
        this.measurements = measurements;
        this.priorityRules = priorityRules;
        this.objects = objects;
        this.positions = positions;
    }

    public void recompute(UUID objectId) {
        Integer categoryId = objects.findById(objectId)
                .map(ObjectRow::categoryId)
                .orElse(null);
        List<PreferenceCandidate> candidates = measurements.findByObject(objectId).stream()
                .map(m -> new PreferenceCandidate(m.id(), m.propertyCode(), m.qualifier(), m.sourceId(), m.valueNum(),
                        m.errMinus(), m.errPlus(), m.retrievedAt()))
                .toList();
        Set<Long> preferred = PreferencePolicy.choosePreferred(candidates, priorityRules.findAll(), categoryId);
        measurements.applyPreferred(objectId, preferred);
        positions.recomputePreferred(objectId);
    }
}
