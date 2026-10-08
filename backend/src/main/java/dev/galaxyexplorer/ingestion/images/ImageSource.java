package dev.galaxyexplorer.ingestion.images;

/** Fontes que o comando {@code images} pode rodar. */
public enum ImageSource {
    /** Recorte CDS hips2fits (qualquer objeto com posicao). */
    CUTOUT,
    ESA_HUBBLE,
    ESA_WEBB
}
