package dev.galaxyexplorer.ingestion.connector.hips2fits;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.galaxyexplorer.ingestion.connector.SourceQueryException;

/**
 * Arquivo {@code properties} de um HiPS. Formato observado nas fixtures reais
 * (DSS2 color, PanSTARRS DR1 color): uma {@code chave = valor} por linha, com
 * padding; linha iniciada por {@code #} e comentario (inclusive chaves
 * comentadas); linhas em branco. Nenhuma chave repetida nas fixtures; se
 * aparecer, a ultima vence. Puro.
 */
public final class HipsPropertiesParser {

    private HipsPropertiesParser() {
    }

    public static Map<String, String> parse(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            out.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
        }
        return out;
    }

    /**
     * @throws SourceQueryException sem {@code obs_title}, {@code obs_copyright} ou {@code hips_license}
     *         (sem credito/licenca nao ha recorte), ou se {@code creator_did} nao for do HiPS pedido
     */
    public static HipsSurveyMetadata toMetadata(Map<String, String> props, HipsSurvey survey) {
        String creatorDid = blankToNull(props.get("creator_did"));
        if (creatorDid != null && !creatorDid.endsWith("/" + survey.hipsId()) && !creatorDid.equals(survey.hipsId())
                && !creatorDid.equals("ivo://" + survey.hipsId())) {
            throw new SourceQueryException("properties de " + survey.propertiesUrl() + " é de outro HiPS: creator_did="
                    + creatorDid + ", esperado " + survey.hipsId());
        }
        return new HipsSurveyMetadata(survey.hipsId(), required(props, "obs_title", survey),
                required(props, "obs_copyright", survey), blankToNull(props.get("obs_copyright_url")),
                required(props, "hips_license", survey), blankToNull(props.get("hips_copyright")),
                blankToNull(props.get("bib_reference")), creatorDid);
    }

    private static String required(Map<String, String> props, String key, HipsSurvey survey) {
        String value = blankToNull(props.get(key));
        if (value == null) {
            throw new SourceQueryException("properties de " + survey.hipsId() + " sem '" + key + "'");
        }
        return value;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
