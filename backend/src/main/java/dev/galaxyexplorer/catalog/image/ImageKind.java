package dev.galaxyexplorer.catalog.image;

import java.util.Locale;

/** Espelha o CHECK de {@code object_image.kind} (V1). */
public enum ImageKind {
    /** Reservado para imagem "crua" de missao (nenhuma fonte deste bloco produz). */
    OBSERVATION,
    /** Recorte de survey (hips2fits). */
    SURVEY_CUTOUT,
    /** Imagem de divulgacao ESA (AVM Type = Observation): composicao colorida processada. */
    PROCESSED_COMPOSITE,
    /** AVM Type = Artwork. Nunca hero (CHECK no banco). */
    ARTIST_IMPRESSION,
    /** AVM Type = Chart. Nunca hero (CHECK no banco). */
    DIAGRAM;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ImageKind fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    /** Ilustracao e diagrama nunca representam o objeto como foto. */
    public boolean canBeHero() {
        return this == OBSERVATION || this == SURVEY_CUTOUT || this == PROCESSED_COMPOSITE;
    }
}
