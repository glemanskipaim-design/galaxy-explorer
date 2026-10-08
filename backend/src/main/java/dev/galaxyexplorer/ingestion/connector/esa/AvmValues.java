package dev.galaxyexplorer.ingestion.connector.esa;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Valores do JSON AVM da ESA, que mistura formas — o ponto mais fragil do Bloco 3.
 * So as formas VISTAS nas fixtures reais sao aceitas:
 * <ul>
 *   <li>string normal ({@code "Observation"}, {@code "ICRS"}, {@code "2005-04-25T06:00:00"});</li>
 *   <li>repr de bytes Python: {@code "b'…'"} ou {@code "b\"…\""}, com escapes {@code \xNN} (bytes UTF-8),
 *       {@code \n}, {@code \t}, {@code \r}, {@code \\}, {@code \'}, {@code \"};</li>
 *   <li>array JSON real (listas: {@code Subject.Name}, {@code Facility}, {@code Spatial.*}), com itens
 *       {@code null} possiveis.</li>
 * </ul>
 * Qualquer outra forma (ex. lista como string {@code "['a', 'b']"}, escape desconhecido, UTF-8
 * invalido) = {@link IllegalArgumentException} com o trecho: a candidata e rejeitada com motivo, nunca
 * "adivinhada". Puro.
 */
public final class AvmValues {

    private AvmValues() {
    }

    /** Texto do campo; {@code null} se ausente, nulo ou vazio. */
    public static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isArray() || node.isObject()) {
            throw new IllegalArgumentException("esperado texto, recebido " + abbreviate(node.toString()));
        }
        String decoded = decode(node.asString());
        return decoded == null || decoded.isEmpty() ? null : decoded;
    }

    /** Itens nao nulos e nao vazios do campo; lista vazia se ausente. */
    public static List<String> list(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node == null || node.isNull() || node.isMissingNode()) {
            return out;
        }
        if (node.isArray()) {
            for (JsonNode item : node.values()) {
                String value = text(item);
                if (value != null) {
                    out.add(value);
                }
            }
            return out;
        }
        String single = text(node);
        if (single != null) {
            if (single.startsWith("[")) {
                throw new IllegalArgumentException("lista codificada como texto (forma não vista nas fixtures): "
                        + abbreviate(single));
            }
            out.add(single);
        }
        return out;
    }

    /** Desfaz o repr de bytes Python; string que nao e repr volta como veio. */
    static String decode(String raw) {
        if (raw == null) {
            return null;
        }
        boolean single = raw.length() >= 3 && raw.startsWith("b'") && raw.endsWith("'");
        boolean dbl = raw.length() >= 3 && raw.startsWith("b\"") && raw.endsWith("\"");
        if (!single && !dbl) {
            return raw;
        }
        String inner = raw.substring(2, raw.length() - 1);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(inner.length());
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c != '\\') {
                byte[] utf8 = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bytes.write(utf8, 0, utf8.length);
                continue;
            }
            if (i + 1 >= inner.length()) {
                throw new IllegalArgumentException("barra invertida no fim de " + abbreviate(raw));
            }
            char e = inner.charAt(++i);
            switch (e) {
                case 'x' -> {
                    if (i + 2 >= inner.length()) {
                        throw new IllegalArgumentException("escape \\x incompleto em " + abbreviate(raw));
                    }
                    String hex = inner.substring(i + 1, i + 3);
                    if (!hex.matches("[0-9a-fA-F]{2}")) {
                        throw new IllegalArgumentException("escape \\x inválido em " + abbreviate(raw));
                    }
                    bytes.write(Integer.parseInt(hex, 16));
                    i += 2;
                }
                case 'n' -> bytes.write('\n');
                case 't' -> bytes.write('\t');
                case 'r' -> bytes.write('\r');
                case '\\', '\'', '"' -> bytes.write(e);
                default -> throw new IllegalArgumentException("escape desconhecido \\" + e + " em " + abbreviate(raw));
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("bytes não são UTF-8 válido em " + abbreviate(raw), ex);
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 80 ? "'" + s + "'" : "'" + s.substring(0, 80) + "…'";
    }
}
