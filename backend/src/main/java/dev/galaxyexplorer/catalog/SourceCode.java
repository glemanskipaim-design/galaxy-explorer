package dev.galaxyexplorer.catalog;

/**
 * Fontes conhecidas pelo sistema. {@link #name()} e exatamente o
 * {@code source.code} gravado no banco (V3 e V8).
 */
public enum SourceCode {
    SIMBAD,
    NASA_EXO,
    /** Recortes de survey (Bloco 3). */
    CDS_HIPS2FITS,
    ESA_HUBBLE,
    ESA_WEBB
}
