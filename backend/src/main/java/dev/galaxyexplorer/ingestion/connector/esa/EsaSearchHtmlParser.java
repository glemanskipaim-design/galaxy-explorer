package dev.galaxyexplorer.ingestion.connector.esa;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.galaxyexplorer.ingestion.connector.SourceQueryException;

/**
 * Ids de imagem do HTML de busca da ESA. Formato real (fixtures de 2026-10-07):
 * os resultados NAO sao links {@code href="/images/<id>/"} — vem num bloco de
 * script {@code var images = [ { id: '<id>', title: …, url: '/images/<id>/' }, … ];}.
 * Busca sem resultado: {@code No images were found.} Qualquer outra forma =
 * {@link SourceQueryException} (o HTML mudou; nunca "zero resultados" em silencio).
 * Sem parser HTML novo: regex ancorada no bloco. Puro.
 */
public final class EsaSearchHtmlParser {

    static final String NO_RESULTS = "No images were found.";
    /** Hubble fecha com {@code ];} na coluna 0; Webb indenta o bloco inteiro (fixtures reais). */
    private static final Pattern BLOCK = Pattern.compile("var images = \\[(.*?)\\n[ \\t]*\\];", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("(?m)^\\s*id: '([^'\\n]*)',\\s*$");

    private EsaSearchHtmlParser() {
    }

    /** Ids na ordem da pagina (ranking da ESA), sem duplicatas; ids fora do formato sao ignorados. */
    public static List<String> imageIds(String html) {
        Matcher block = BLOCK.matcher(html);
        if (!block.find()) {
            if (html.contains(NO_RESULTS)) {
                return List.of();
            }
            throw new SourceQueryException("HTML de busca da ESA em formato desconhecido (sem 'var images' nem '"
                    + NO_RESULTS + "') — provável mudança no site");
        }
        Set<String> ids = new LinkedHashSet<>();
        Matcher id = ID.matcher(block.group(1));
        while (id.find()) {
            String value = id.group(1);
            if (EsaSite.IMAGE_ID.matcher(value).matches()) {
                ids.add(value);
            }
        }
        return new ArrayList<>(ids);
    }
}
