package dev.galaxyexplorer.catalog.image;

import java.time.LocalDate;

/**
 * O que a {@link HeroPolicy} precisa saber de uma imagem ja gravada.
 *
 * @param sourceCode       {@code source.code} ({@code ESA_WEBB}, {@code ESA_HUBBLE}, {@code CDS_HIPS2FITS})
 * @param curatedHero      curadoria manual mandou esta ser o hero
 * @param coverageRatio    menor lado do quadro / tamanho angular do objeto; {@code null} = desconhecido
 * @param releaseDate      pode ser {@code null}
 * @param separationArcmin pode ser {@code null}
 */
public record HeroCandidate(long id, ImageKind kind, String sourceCode, boolean curatedHero, Double coverageRatio,
                            LocalDate releaseDate, Double separationArcmin) {
}
