package dev.galaxyexplorer.catalog.measurement;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import dev.galaxyexplorer.catalog.Epistemic;

/**
 * Uma medicao a gravar, com proveniencia completa. Monte com {@link #builder}
 * (campos nomeados; nunca o construtor posicional de 19 argumentos).
 *
 * <p>Contrato: quem monta a medicao a partir da fonte preenche {@code bibcode}
 * (texto) e deixa {@code referenceId} nulo; o {@code CatalogWriter} resolve
 * {@code bibcode -> referenceId} e passa a copia ({@link #withReferenceId}) ao
 * repositorio, que grava so {@code referenceId}.
 *
 * <p>Campos anulaveis: {@code valueNum, errMinus, errPlus, limitFlag, valueText,
 * originalUnit, originalError, method, bibcode, referenceId, rawPayloadId}.
 *
 * @param originalError incerteza exatamente como a fonte publicou (mesmo se descartada por invalida)
 */
public record MeasurementWrite(String propertyCode, String qualifier, Double valueNum, Double errMinus,
                               Double errPlus, String limitFlag, String valueText, String originalValue,
                               String originalUnit, String originalError, Epistemic epistemic, String method,
                               List<Long> derivedFrom, String dataset, String sourceUrl, String bibcode,
                               Long referenceId, Long rawPayloadId, Instant retrievedAt) {

    public MeasurementWrite {
        Objects.requireNonNull(propertyCode, "propertyCode");
        Objects.requireNonNull(qualifier, "qualifier");
        Objects.requireNonNull(originalValue, "originalValue");
        Objects.requireNonNull(epistemic, "epistemic");
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(retrievedAt, "retrievedAt");
        derivedFrom = derivedFrom == null ? List.of() : List.copyOf(derivedFrom);
    }

    public static Builder builder(String propertyCode, Epistemic epistemic, String dataset, String sourceUrl,
                                  Instant retrievedAt) {
        return new Builder(propertyCode, epistemic, dataset, sourceUrl, retrievedAt);
    }

    public MeasurementWrite withReferenceId(Long id) {
        return new MeasurementWrite(propertyCode, qualifier, valueNum, errMinus, errPlus, limitFlag, valueText,
                originalValue, originalUnit, originalError, epistemic, method, derivedFrom, dataset, sourceUrl,
                bibcode, id, rawPayloadId, retrievedAt);
    }

    public MeasurementKey key(short sourceId) {
        return new MeasurementKey(propertyCode, qualifier, sourceId, dataset);
    }

    public static final class Builder {
        private final String propertyCode;
        private final Epistemic epistemic;
        private final String dataset;
        private final String sourceUrl;
        private final Instant retrievedAt;
        private String qualifier = "";
        private Double valueNum;
        private Double errMinus;
        private Double errPlus;
        private String limitFlag;
        private String valueText;
        private String originalValue;
        private String originalUnit;
        private String originalError;
        private String method;
        private List<Long> derivedFrom = List.of();
        private String bibcode;
        private Long referenceId;
        private Long rawPayloadId;

        private Builder(String propertyCode, Epistemic epistemic, String dataset, String sourceUrl,
                        Instant retrievedAt) {
            this.propertyCode = propertyCode;
            this.epistemic = epistemic;
            this.dataset = dataset;
            this.sourceUrl = sourceUrl;
            this.retrievedAt = retrievedAt;
        }

        public Builder qualifier(String qualifier) {
            this.qualifier = qualifier;
            return this;
        }

        public Builder value(Double valueNum) {
            this.valueNum = valueNum;
            return this;
        }

        public Builder errors(Double errMinus, Double errPlus) {
            this.errMinus = errMinus;
            this.errPlus = errPlus;
            return this;
        }

        public Builder limitFlag(String limitFlag) {
            this.limitFlag = limitFlag;
            return this;
        }

        public Builder text(String valueText) {
            this.valueText = valueText;
            return this;
        }

        public Builder original(String originalValue, String originalUnit) {
            this.originalValue = originalValue;
            this.originalUnit = originalUnit;
            return this;
        }

        public Builder originalError(String originalError) {
            this.originalError = originalError;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder derivedFrom(List<Long> derivedFrom) {
            this.derivedFrom = derivedFrom;
            return this;
        }

        public Builder bibcode(String bibcode) {
            this.bibcode = bibcode;
            return this;
        }

        public Builder referenceId(Long referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        public Builder rawPayloadId(Long rawPayloadId) {
            this.rawPayloadId = rawPayloadId;
            return this;
        }

        public MeasurementWrite build() {
            return new MeasurementWrite(propertyCode, qualifier, valueNum, errMinus, errPlus, limitFlag, valueText,
                    originalValue, originalUnit, originalError, epistemic, method, derivedFrom, dataset, sourceUrl,
                    bibcode, referenceId, rawPayloadId, retrievedAt);
        }
    }
}
