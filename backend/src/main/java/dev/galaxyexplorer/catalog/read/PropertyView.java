package dev.galaxyexplorer.catalog.read;

import java.util.List;

/**
 * Uma propriedade de um objeto: o valor preferido + alternativas, ou a
 * ausencia com motivo.
 *
 * @param absenceReason so quando {@code available == false}
 * @param absenceSnr    S/N da paralaxe quando o motivo envolve S/N; senao {@code null}
 * @param preferred     {@code null} quando indisponivel
 */
public record PropertyView(String code, String labelPt, String displayGroup, String qualifier, boolean available,
                           AbsenceReason absenceReason, Double absenceSnr, MeasurementView preferred,
                           List<MeasurementView> alternatives) {

    public PropertyView {
        alternatives = List.copyOf(alternatives);
    }
}
