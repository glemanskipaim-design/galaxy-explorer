package dev.galaxyexplorer.catalog.measurement;

import java.time.Instant;

/** O que a politica de preferencia precisa saber de uma medicao. Valores/erros podem ser {@code null}. */
public record PreferenceCandidate(long id, String propertyCode, String qualifier, short sourceId, Double valueNum,
                                  Double errMinus, Double errPlus, Instant retrievedAt) {
}
