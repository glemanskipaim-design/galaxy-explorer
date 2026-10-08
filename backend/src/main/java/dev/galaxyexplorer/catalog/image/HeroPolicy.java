package dev.galaxyexplorer.catalog.image;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Qual imagem e o hero de um objeto e em que ordem as demais aparecem na
 * galeria. Puro. Ordem (decisao do usuario, ADENDO do plano do Bloco 3):
 * <ol>
 *   <li>so {@link ImageKind#canBeHero()} pode ser hero (ilustracao/diagrama vao para o fim da galeria);</li>
 *   <li>curadoria manual {@code hero} vence tudo;</li>
 *   <li>imagem ESA vence o recorte de survey, <b>exceto</b> quando o quadro dela cobre bem menos
 *       que o objeto: menor lado do quadro &lt; {@value #MIN_COVERAGE_FOR_ESA_HERO} x tamanho angular
 *       (eixo maior). Ai o recorte vira hero e a ESA vai para a galeria (M31, M42, M16 nos dados reais);</li>
 *   <li>entre imagens do mesmo grupo: cobre o objeto inteiro (razao &ge; 1) &gt; cobertura desconhecida &gt;
 *       cobertura parcial; depois publicacao mais recente; ESA/Webb antes de ESA/Hubble; menor
 *       separacao; menor id.</li>
 * </ol>
 * Cobertura desconhecida (sem tamanho angular no catalogo ou sem dimensoes na AVM) nao rebaixa a ESA:
 * "cobre bem menos" exige os dois numeros.
 */
public final class HeroPolicy {

    public static final double MIN_COVERAGE_FOR_ESA_HERO = 0.5;

    private static final Comparator<HeroCandidate> ORDER = Comparator
            .comparingInt((HeroCandidate c) -> c.kind().canBeHero() ? 0 : 1)
            .thenComparingInt(c -> c.curatedHero() ? 0 : 1)
            .thenComparingInt(HeroPolicy::tier)
            .thenComparingInt(HeroPolicy::coverageClass)
            .thenComparing(HeroCandidate::releaseDate, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()))
            .thenComparingInt(c -> sourceRank(c.sourceCode()))
            .thenComparing(HeroCandidate::separationArcmin, Comparator.nullsLast(Comparator.<Double>naturalOrder()))
            .thenComparingLong(HeroCandidate::id);

    private HeroPolicy() {
    }

    /** Todas as imagens, na ordem de exibicao (indice = {@code gallery_rank}). */
    public static List<HeroCandidate> order(List<HeroCandidate> candidates) {
        return candidates.stream().sorted(ORDER).toList();
    }

    /** Id do hero; vazio se nenhuma imagem pode ser hero. */
    public static Optional<Long> choose(List<HeroCandidate> candidates) {
        return order(candidates).stream().findFirst().filter(c -> c.kind().canBeHero()).map(HeroCandidate::id);
    }

    /** 0 = ESA com cobertura suficiente ou desconhecida; 1 = recorte de survey; 2 = ESA que cobre bem menos que o objeto. */
    static int tier(HeroCandidate c) {
        if (c.kind() == ImageKind.SURVEY_CUTOUT) {
            return 1;
        }
        return coversFarLess(c.coverageRatio()) ? 2 : 0;
    }

    public static boolean coversFarLess(Double coverageRatio) {
        return coverageRatio != null && coverageRatio < MIN_COVERAGE_FOR_ESA_HERO;
    }

    private static int coverageClass(HeroCandidate c) {
        if (c.coverageRatio() == null) {
            return 1;
        }
        return c.coverageRatio() >= 1.0 ? 0 : 2;
    }

    private static int sourceRank(String sourceCode) {
        return switch (sourceCode) {
            case "ESA_WEBB" -> 0;
            case "ESA_HUBBLE" -> 1;
            default -> 2;
        };
    }
}
