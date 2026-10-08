package dev.galaxyexplorer.support;

import java.util.List;
import java.util.UUID;

import dev.galaxyexplorer.ingestion.connector.image.ImageTarget;
import dev.galaxyexplorer.ingestion.connector.tap.TapJsonParser;
import dev.galaxyexplorer.ingestion.connector.tap.TapResult;
import dev.galaxyexplorer.ingestion.connector.tap.TapRow;

/**
 * {@link ImageTarget} montado das respostas REAIS do SIMBAD gravadas em {@code fixtures/simbad/}:
 * posicao ({@code ra}, {@code dec}), tamanho angular ({@code galdim_majaxis}) e identificadores
 * ({@code ident}). Nada digitado.
 */
public final class RealTargets {

    private RealTargets() {
    }

    /**
     * @param prefix   par de fixtures ({@code cz}, {@code sample}, {@code m16})
     * @param mainId   {@code main_id} exato no {@code basic} ({@code "M  51"}, {@code "* alf Ori"})
     */
    public static ImageTarget of(String prefix, String mainId) {
        TapResult basic = TapJsonParser.parse(Fixtures.read("simbad/" + prefix + "_basic.json"));
        TapRow row = basic.rows().stream().filter(r -> mainId.equals(r.rawText("main_id").orElse(null))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(mainId + " ausente em " + prefix + "_basic"));
        long oid = row.integer("oid").orElseThrow();
        TapResult ident = TapJsonParser.parse(Fixtures.read("simbad/" + prefix + "_ident.json"));
        List<String> ids = ident.rows().stream().filter(r -> r.integer("oidref").orElse(-1L) == oid)
                .map(r -> r.text("id").orElseThrow()).toList();
        Double size = row.number("galdim_majaxis").orElse(null);
        String slug = mainId.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return new ImageTarget(UUID.nameUUIDFromBytes(mainId.getBytes()), slug, mainId, ids,
                row.number("ra").orElse(null), row.number("dec").orElse(null), "SIMBAD", size,
                size == null ? null : 1L, size == null ? null : "SIMBAD");
    }
}
