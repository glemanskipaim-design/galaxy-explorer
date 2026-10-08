package dev.galaxyexplorer.ingestion.connector.esa;

import dev.galaxyexplorer.catalog.SourceCode;

/**
 * Os dois sites de divulgacao da ESA. Valores conferidos ao vivo em 2026-10-07
 * (fixtures/README.md): {@code Facility} exatamente como a AVM grava.
 */
public enum EsaProvider {
    HUBBLE(SourceCode.ESA_HUBBLE, "ESA/Hubble", "Hubble Space Telescope", "https://esahubble.org/copyright/"),
    WEBB(SourceCode.ESA_WEBB, "ESA/Webb", "James Webb Space Telescope", "https://esawebb.org/copyright/");

    private final SourceCode source;
    private final String label;
    private final String expectedFacility;
    private final String termsUrl;

    EsaProvider(SourceCode source, String label, String expectedFacility, String termsUrl) {
        this.source = source;
        this.label = label;
        this.expectedFacility = expectedFacility;
        this.termsUrl = termsUrl;
    }

    public SourceCode source() {
        return source;
    }

    /** {@code "ESA/Hubble"}, {@code "ESA/Webb"} — tambem o rotulo curto das miniaturas. */
    public String label() {
        return label;
    }

    /** Valor de AVM {@code Facility} que prova que a imagem e do proprio telescopio. */
    public String expectedFacility() {
        return expectedFacility;
    }

    public String termsUrl() {
        return termsUrl;
    }
}
