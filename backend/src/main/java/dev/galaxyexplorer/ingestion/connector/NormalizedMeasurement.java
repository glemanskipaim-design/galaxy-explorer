package dev.galaxyexplorer.ingestion.connector;

import java.util.Objects;

import dev.galaxyexplorer.catalog.Epistemic;

/**
 * Uma medicao no modelo canonico, ja na unidade canonica da propriedade
 * ({@code originalValue}/{@code originalUnit}/{@code originalError} preservam o que veio).
 * Monte com {@link #builder}: campo nomeado, nunca posicional (trocar {@code errMinus}
 * por {@code errPlus} num construtor de 15 argumentos compilaria sem aviso).
 *
 * @param originalError  incerteza exatamente como a fonte publicou — inclusive quando foi
 *                       descartada por invalida ({@code errMinus}/{@code errPlus} nulos); {@code null}
 *                       quando a fonte nao publicou incerteza
 * @param dataset        entra na chave unica da medicao
 * @param payloadDataset de qual {@link RawPayload} do lote veio (para {@code raw_payload_id})
 */
public record NormalizedMeasurement(String propertyCode, String qualifier, Double valueNum, Double errMinus,
                                    Double errPlus, String limitFlag, String valueText, String originalValue,
                                    String originalUnit, String originalError, Epistemic epistemic, String method,
                                    String bibcode, String dataset, String payloadDataset) {

    public NormalizedMeasurement {
        Objects.requireNonNull(propertyCode, "propertyCode");
        Objects.requireNonNull(qualifier, "qualifier");
        Objects.requireNonNull(originalValue, "originalValue");
        Objects.requireNonNull(epistemic, "epistemic");
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(payloadDataset, "payloadDataset");
    }

    /** Campos obrigatorios aqui; o resto pelo builder (qualifier default {@code ""}). */
    public static Builder builder(String propertyCode, Epistemic epistemic, String dataset, String payloadDataset) {
        return new Builder(propertyCode, epistemic, dataset, payloadDataset);
    }

    public static final class Builder {
        private final String propertyCode;
        private final Epistemic epistemic;
        private final String dataset;
        private final String payloadDataset;
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
        private String bibcode;

        private Builder(String propertyCode, Epistemic epistemic, String dataset, String payloadDataset) {
            this.propertyCode = propertyCode;
            this.epistemic = epistemic;
            this.dataset = dataset;
            this.payloadDataset = payloadDataset;
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

        public Builder symmetricError(Double err) {
            return errors(err, err);
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

        public Builder bibcode(String bibcode) {
            this.bibcode = bibcode;
            return this;
        }

        public NormalizedMeasurement build() {
            return new NormalizedMeasurement(propertyCode, qualifier, valueNum, errMinus, errPlus, limitFlag,
                    valueText, originalValue, originalUnit, originalError, epistemic, method, bibcode, dataset,
                    payloadDataset);
        }
    }
}
