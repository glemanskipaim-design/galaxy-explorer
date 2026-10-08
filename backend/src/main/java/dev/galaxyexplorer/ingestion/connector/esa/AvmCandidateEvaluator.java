package dev.galaxyexplorer.ingestion.connector.esa;

import java.net.URI;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import dev.galaxyexplorer.catalog.image.ImageKind;
import dev.galaxyexplorer.catalog.position.AngularSeparation;
import dev.galaxyexplorer.ingestion.connector.image.ImageCandidate;
import dev.galaxyexplorer.ingestion.connector.image.ImageTarget;

/**
 * Uma imagem ESA (AVM) serve para um objeto? Regras, nesta ordem (a primeira que
 * falha da o motivo da rejeicao):
 * <ol>
 *   <li>{@code Type}: selecao automatica so aceita {@code Observation} ({@code processed_composite});
 *       curadoria aceita tambem {@code Artwork} ({@code artist_impression}) e {@code Chart} ({@code diagram});</li>
 *   <li>{@code Facility} contem o telescopio do site (so na automatica: o arquivo da ESA/Hubble publica
 *       tambem imagens de contexto de solo, ex. DSS2);</li>
 *   <li>licenca conhecida ({@link EsaLicenseRule}), {@code Credit} presente e sem marcacao HTML (forma nao
 *       vista nas fixtures: rejeitada, nao "limpa");</li>
 *   <li>{@code Subject.Name} casa com um identificador do objeto ({@link SubjectNameMatcher}) — obrigatorio
 *       tambem na curadoria: ela escolhe a imagem, nao inventa o vinculo;</li>
 *   <li>espacial (posicao do objeto x {@code Spatial.ReferenceValue}, frame ICRS/FK5):
 *     <ul>
 *       <li>com escala e dimensoes: aceita se o centro do objeto cai dentro do quadro (circulo circunscrito,
 *           meia-diagonal, folgado pelo deslocamento do pixel de referencia) OU se o centro da imagem cai
 *           dentro da extensao do objeto (metade do tamanho angular) — a segunda forma e a de imagens de um
 *           pedaco de objeto grande (Pilares da Criacao em M16, PHAT em M31), verificada nos dados reais;</li>
 *       <li>sem dimensoes: tolerancia {@code max(tamanho/2, 5′)};</li>
 *       <li>sem coordenadas: automatica rejeita ("não verificável"); curada aceita e diz isso.</li>
 *     </ul>
 * </ol>
 * Puro.
 */
public final class AvmCandidateEvaluator {

    static final double MIN_TOLERANCE_ARCMIN = 5.0;
    private static final Pattern HTML_TAG = Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");
    private static final Set<String> FRAMES = Set.of("ICRS", "FK5");

    /** Resultado da avaliacao. */
    public sealed interface Evaluation permits Accepted, Rejected {
    }

    public record Accepted(ImageCandidate candidate) implements Evaluation {
    }

    public record Rejected(String reason) implements Evaluation {
    }

    private AvmCandidateEvaluator() {
    }

    public static Evaluation evaluate(EsaSite site, AvmRecord r, ImageTarget t, boolean curated, boolean curatedHero) {
        EsaProvider p = site.provider();
        ImageKind kind = kind(r.type(), curated);
        if (kind == null) {
            return new Rejected("Type '" + r.type() + "' não aceito " + (curated ? "nem na curadoria" : "na seleção automática"));
        }
        if (!curated && !r.facilities().contains(p.expectedFacility())) {
            return new Rejected("Facility " + r.facilities() + " sem '" + p.expectedFacility()
                    + "' (imagem de contexto, não do próprio telescópio)");
        }
        Optional<String> license = EsaLicenseRule.licenseCode(r.rights());
        if (license.isEmpty()) {
            return new Rejected("Rights '" + r.rights() + "' não é uma licença conhecida");
        }
        if (r.credit() == null || r.credit().isBlank()) {
            return new Rejected("sem Credit");
        }
        if (HTML_TAG.matcher(r.credit()).find()) {
            return new Rejected("Credit com marcação HTML (forma não vista nas fixtures)");
        }
        if (r.referenceUrl() == null || !r.referenceUrl().startsWith("https://")) {
            return new Rejected("ReferenceURL ausente ou não-https: " + r.referenceUrl());
        }
        Optional<String> nameMatch = SubjectNameMatcher.match(r.subjectNames(), t.identifiers());
        if (nameMatch.isEmpty()) {
            return new Rejected("Subject.Name " + r.subjectNames() + " não casa com nenhum identificador do objeto");
        }
        if (!t.hasPosition()) {
            return new Rejected("objeto sem posição no catálogo — vínculo espacial não verificável");
        }

        Double angSize = t.angSizeMajorArcmin() != null && t.angSizeMajorArcmin() > 0 ? t.angSizeMajorArcmin() : null;
        Double separation = null;
        Double fovDeg = null;
        String fovBasis = null;
        Double coverage = null;
        Long measurementId = null;
        String spatialBasis;
        if (r.refRaDeg() == null || r.refDecDeg() == null) {
            if (!curated) {
                return new Rejected("sem coordenadas na AVM (Spatial.ReferenceValue) — não verificável");
            }
            spatialBasis = "sem coordenadas na AVM: vínculo pela curadoria manual + nome";
        } else {
            if (r.coordinateFrame() == null || !FRAMES.contains(r.coordinateFrame().trim().toUpperCase(Locale.ROOT))) {
                return new Rejected("Spatial.CoordinateFrame '" + r.coordinateFrame() + "' não suportado (só ICRS/FK5)");
            }
            separation = AngularSeparation.arcminutes(t.raDeg(), t.decDeg(), r.refRaDeg(), r.refDecDeg());
            if (r.hasFrame()) {
                double wArcmin = r.frameWidthDeg() * 60.0;
                double hArcmin = r.frameHeightDeg() * 60.0;
                double halfDiagonal = 0.5 * Math.hypot(wArcmin, hArcmin) + referenceOffsetArcmin(r);
                boolean inFrame = separation <= halfDiagonal;
                boolean inExtent = angSize != null && separation <= angSize / 2.0;
                String frame = num(wArcmin) + "′ × " + num(hArcmin) + "′";
                if (!inFrame && !inExtent) {
                    return new Rejected("centro da imagem a " + num(separation) + "′ do objeto: fora do quadro ("
                            + frame + ", meia-diagonal " + num(halfDiagonal) + "′)"
                            + (angSize == null ? " e objeto sem tamanho angular"
                                    : " e fora da extensão do objeto (raio " + num(angSize / 2.0) + "′)"));
                }
                fovDeg = Math.max(r.frameWidthDeg(), r.frameHeightDeg());
                fovBasis = "quadro da AVM: |Spatial.Scale| × Spatial.ReferenceDimension = " + frame;
                if (angSize != null) {
                    coverage = Math.min(wArcmin, hArcmin) / angSize;
                    measurementId = t.angSizeMeasurementId();
                }
                spatialBasis = "centro da imagem a " + num(separation) + "′ da posição do objeto — "
                        + (inFrame ? "objeto dentro do quadro (" + frame + ")"
                                : "fora do quadro (" + frame + "), mas dentro da extensão do objeto (raio "
                                        + num(angSize / 2.0) + "′)");
                if (coverage != null) {
                    spatialBasis += "; menor lado do quadro = " + num(coverage * 100) + "% do tamanho angular ("
                            + num(angSize) + "′)";
                }
            } else {
                double tolerance = Math.max(angSize == null ? 0 : angSize / 2.0, MIN_TOLERANCE_ARCMIN);
                if (separation > tolerance) {
                    return new Rejected("centro da imagem a " + num(separation) + "′ do objeto, acima da tolerância de "
                            + num(tolerance) + "′ (AVM sem dimensões do quadro)");
                }
                spatialBasis = "centro da imagem a " + num(separation) + "′ da posição do objeto (tolerância "
                        + num(tolerance) + "′; AVM sem dimensões do quadro)";
            }
        }

        URI download = site.screenUrl(r.id());
        return new Accepted(new ImageCandidate(p.source(), r.id(), kind, r.title(), p.label(), p.label(), r.credit(),
                r.rights(), license.get(), p.termsUrl(), joinDistinct(r.facilities()), joinDistinct(r.instruments()),
                joinDistinct(r.spectralBands()), r.referenceUrl(), r.date(), r.refRaDeg(), r.refDecDeg(), fovDeg,
                fovBasis, coverage, separation, measurementId,
                (curated ? "curadoria manual; " : "") + nameMatch.get() + "; " + spatialBasis, download, curated,
                curatedHero && kind.canBeHero()));
    }

    static ImageKind kind(String type, boolean curated) {
        if ("Observation".equals(type)) {
            return ImageKind.PROCESSED_COMPOSITE;
        }
        if (curated && "Artwork".equals(type)) {
            return ImageKind.ARTIST_IMPRESSION;
        }
        if (curated && "Chart".equals(type)) {
            return ImageKind.DIAGRAM;
        }
        return null;
    }

    /** Distancia (arcmin) entre o pixel de referencia e o centro geometrico do quadro. */
    private static double referenceOffsetArcmin(AvmRecord r) {
        if (r.referencePixel() == null) {
            return 0;
        }
        double dx = (r.referencePixel()[0] - r.dimensionPx()[0] / 2.0) * Math.abs(r.scaleDegPerPx()[0]);
        double dy = (r.referencePixel()[1] - r.dimensionPx()[1] / 2.0) * Math.abs(r.scaleDegPerPx()[1]);
        return Math.hypot(dx, dy) * 60.0;
    }

    static String joinDistinct(List<String> values) {
        Set<String> distinct = new LinkedHashSet<>(values);
        return distinct.isEmpty() ? null : String.join(", ", distinct);
    }

    static String num(double v) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR"))).format(v);
    }
}
