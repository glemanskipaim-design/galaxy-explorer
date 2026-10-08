package dev.galaxyexplorer.ingestion.connector.esa;

import java.util.Optional;

/**
 * Licenca de uma imagem ESA a partir do AVM {@code Rights} LITERAL. So o texto
 * visto nas fixtures reais vira licenca; qualquer outro texto = sem licenca
 * conhecida = imagem rejeitada (nunca presumir). Puro.
 */
public final class EsaLicenseRule {

    static final String CC_BY_4_TEXT = "Creative Commons Attribution 4.0 International License";

    private EsaLicenseRule() {
    }

    public static Optional<String> licenseCode(String rights) {
        if (rights != null && CC_BY_4_TEXT.equals(rights.trim())) {
            return Optional.of("CC-BY-4.0");
        }
        return Optional.empty();
    }
}
