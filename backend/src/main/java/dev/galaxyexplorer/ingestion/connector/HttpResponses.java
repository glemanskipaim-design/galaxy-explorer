package dev.galaxyexplorer.ingestion.connector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatusCode;

/**
 * Regra unica de classificacao de resposta HTTP das fontes (TAP, Sesame...):
 * <ul>
 *   <li>429, 5xx -> {@link SourceUnavailableException} (transitorio, o retry atua);</li>
 *   <li>outros nao-2xx -> {@link SourceQueryException} (permanente, sem retry).</li>
 * </ul>
 * Erro de rede ({@code ResourceAccessException}) e tratado pelos clientes como
 * {@link SourceUnavailableException}.
 */
public final class HttpResponses {

    public static final int MAX_ERROR_BODY_CHARS = 500;

    /**
     * O VOTable de erro do TAP do SIMBAD ecoa o IP publico do cliente em
     * {@code <INFO name="USER" value="<ip> (anonymous)"/>}. Esse dado nunca deve
     * ir para mensagens de erro (que chegam a logs e ao error_summary).
     */
    private static final Pattern USER_INFO = Pattern.compile("<INFO\\s+name=\"USER\"[^>]*/>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private HttpResponses() {
    }

    public static void throwIfFailed(String service, HttpStatusCode status, String body) {
        if (status.value() == 429 || status.is5xxServerError()) {
            throw new SourceUnavailableException(service + " respondeu HTTP " + status.value() + ": "
                    + abbreviate(body));
        }
        if (!status.is2xxSuccessful()) {
            throw new SourceQueryException(service + " recusou a consulta (HTTP " + status.value() + "): "
                    + abbreviate(body));
        }
    }

    public static String readBody(InputStream in) throws IOException {
        return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    /** Uma linha, sem o IP do cliente, no maximo {@value #MAX_ERROR_BODY_CHARS} caracteres. */
    public static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String clean = USER_INFO.matcher(body).replaceAll("");
        String oneLine = WHITESPACE.matcher(clean).replaceAll(" ").trim();
        return oneLine.length() <= MAX_ERROR_BODY_CHARS ? oneLine
                : oneLine.substring(0, MAX_ERROR_BODY_CHARS) + "...";
    }
}
