package dev.galaxyexplorer.support;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.hips2fits.CutoutPlan;
import dev.galaxyexplorer.ingestion.connector.hips2fits.Hips2FitsClient;
import dev.galaxyexplorer.ingestion.connector.hips2fits.Hips2FitsUrls;
import dev.galaxyexplorer.ingestion.connector.hips2fits.HipsSurvey;
import dev.galaxyexplorer.ingestion.connector.image.DownloadedImage;
import dev.galaxyexplorer.media.ImageFileStore;

/**
 * {@link Hips2FitsClient} de teste: devolve as respostas REAIS gravadas.
 * <ul>
 *   <li>{@code properties}: {@code fixtures/hips2fits/<dss2|panstarrs>_color.properties};</li>
 *   <li>{@code cutout}: dois recortes reais no repositorio.
 *     <ul>
 *       <li>M31 no DSS2 ({@code m31_dss2_800x600.jpg}): devolvido so quando a URL que o plano gera
 *           ({@link Hips2FitsUrls#cutout}, primeira URL base) e IGUAL a capturada — prova que o recorte gravado e
 *           exatamente o que o {@code CutoutPlanner} pede para M31;</li>
 *       <li>M51 no PanSTARRS ({@code m51_panstarrs_160x120.jpg}): devolvido quando o plano pede PanSTARRS na
 *           posicao de M51 (mesmos ra/dec da URL capturada, 6 casas) — com a URL CAPTURADA como
 *           {@code requestUrl}, porque o campo/tamanho pedido pelo plano difere do capturado.</li>
 *     </ul>
 *     Qualquer outro recorte = {@link SourceQueryException} ("sem fixture"), o mesmo efeito de um 4xx real: nunca
 *     bytes inventados.</li>
 * </ul>
 */
public class FixtureHips2FitsClient implements Hips2FitsClient {

    public static final String M51_URL = Fixtures.read("hips2fits/m51_panstarrs_160x120.url").trim();
    public static final Instant CAPTURED_AT = Instant.parse("2026-10-07T22:28:30Z");
    public static final String M31_URL = Fixtures.read("hips2fits/m31_dss2_800x600.url").trim();
    public static final Instant M31_CAPTURED_AT = Instant.parse("2026-10-08T22:57:05Z");
    private static final URI BASE = URI.create("https://alasky.cds.unistra.fr/hips-image-services/hips2fits");

    private static final Map<String, String> PROPERTIES = Map.of(
            "CDS/P/DSS2/color", "hips2fits/dss2_color.properties",
            "CDS/P/PanSTARRS/DR1/color-z-zg-g", "hips2fits/panstarrs_color.properties");

    private final AtomicInteger propertiesCalls = new AtomicInteger();
    private final AtomicInteger cutoutCalls = new AtomicInteger();

    @Override
    public RawPayload properties(HipsSurvey survey) {
        propertiesCalls.incrementAndGet();
        String file = PROPERTIES.get(survey.hipsId());
        if (file == null) {
            throw new SourceQueryException("sem fixture de properties para " + survey.hipsId());
        }
        return RawPayload.of(DATASET_PROPERTIES, survey.propertiesUrl().toString(), survey.hipsId(),
                Fixtures.read(file), RawPayload.BodyFormat.TEXT, CAPTURED_AT);
    }

    @Override
    public DownloadedImage cutout(CutoutPlan plan) {
        cutoutCalls.incrementAndGet();
        if (Hips2FitsUrls.cutout(BASE, plan).toString().equals(M31_URL)) {
            byte[] bytes = Fixtures.readBytes("hips2fits/m31_dss2_800x600.jpg");
            return new DownloadedImage(bytes, "image/jpeg", M31_URL, ImageFileStore.sha256Hex(bytes), M31_CAPTURED_AT);
        }
        String ra = "ra=" + decimal(plan.raDeg()) + "&";
        String dec = "dec=" + decimal(plan.decDeg()) + "&";
        if (plan.survey().hipsId().equals("CDS/P/PanSTARRS/DR1/color-z-zg-g") && M51_URL.contains(ra)
                && M51_URL.contains(dec)) {
            byte[] bytes = Fixtures.readBytes("hips2fits/m51_panstarrs_160x120.jpg");
            return new DownloadedImage(bytes, "image/jpeg", M51_URL, ImageFileStore.sha256Hex(bytes), CAPTURED_AT);
        }
        throw new SourceQueryException("hips2fits (fixture): sem recorte capturado para " + plan.survey().hipsId()
                + " em ra=" + plan.raDeg() + " dec=" + plan.decDeg());
    }

    private static String decimal(double v) {
        return java.math.BigDecimal.valueOf(v).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString();
    }

    public int propertiesCalls() {
        return propertiesCalls.get();
    }

    public int cutoutCalls() {
        return cutoutCalls.get();
    }

    public void reset() {
        propertiesCalls.set(0);
        cutoutCalls.set(0);
    }
}
