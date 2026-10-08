package dev.galaxyexplorer.ingestion.connector.hips2fits;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;

import dev.galaxyexplorer.common.Texts;

/**
 * URL de recorte do hips2fits. Parametros documentados pelo servico (pagina da
 * URL base, 2026-10-07): {@code hips, ra, dec, fov, width, height, projection,
 * format}. {@code projection} e obrigatorio sem {@code wcs} ({@code TAN},
 * tangente/gnomonica, a projecao usual de imagem pequena do ceu). Coordenadas
 * com 6 casas decimais (0,0036″ — muito abaixo do pixel). Puro.
 */
public final class Hips2FitsUrls {

    private Hips2FitsUrls() {
    }

    public static URI cutout(URI base, CutoutPlan p) {
        return URI.create(base + "?hips=" + Texts.urlEncode(p.survey().hipsId())
                + "&ra=" + decimal(p.raDeg()) + "&dec=" + decimal(p.decDeg()) + "&fov=" + decimal(p.fovDeg())
                + "&width=" + p.width() + "&height=" + p.height() + "&projection=TAN&format=jpg");
    }

    static String decimal(double v) {
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
