package dev.galaxyexplorer.ingestion.images.curation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import dev.galaxyexplorer.ingestion.connector.esa.EsaProvider;
import dev.galaxyexplorer.ingestion.connector.esa.EsaSite;

/**
 * Curadoria manual de imagens ESA ({@code src/main/resources/images/curation.csv},
 * versionado — revisado no diff, sem migracao a cada ajuste editorial).
 *
 * <pre>
 * object_query,provider,image_id,action,note
 * M 42,HUBBLE,heic0601a,hero,mosaico completo da nebulosa
 * </pre>
 * {@code provider} ∈ {@code HUBBLE|WEBB}; {@code action} ∈ {@code include|hero|exclude}; linha iniciada por
 * {@code #} e comentario. TUDO e validado antes de rodar: qualquer problema aborta o comando com a lista
 * completa ({@link InvalidCurationException}).
 */
public final class CurationFile {

    static final String HEADER = "object_query,provider,image_id,action,note";

    private final List<CurationEntry> entries;

    private CurationFile(List<CurationEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static CurationFile empty() {
        return new CurationFile(List.of());
    }

    public List<CurationEntry> entries() {
        return entries;
    }

    public List<CurationEntry> forObject(UUID objectId, EsaProvider provider) {
        return entries.stream().filter(e -> e.objectId().equals(objectId) && e.provider() == provider).toList();
    }

    /**
     * @param resolveObject termo -> id do objeto pela busca EXATA local; {@code null} = nao resolvido ou ambiguo
     * @throws InvalidCurationException com todos os problemas encontrados
     */
    public static CurationFile parse(String csv, Function<String, UUID> resolveObject) {
        List<CurationEntry> entries = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String[] lines = csv.split("\\R", -1);
        boolean headerSeen = false;
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!headerSeen) {
                if (!HEADER.equals(line)) {
                    problems.add("linha " + lineNo + ": cabeçalho esperado '" + HEADER + "'");
                }
                headerSeen = true;
                continue;
            }
            String[] cols = line.split(",", 5);
            if (cols.length < 4) {
                problems.add("linha " + lineNo + ": esperadas ao menos 4 colunas");
                continue;
            }
            String query = cols[0].strip();
            EsaProvider provider = parseEnum(EsaProvider.class, cols[1].strip());
            String imageId = cols[2].strip();
            CurationEntry.Action action = parseEnum(CurationEntry.Action.class, cols[3].strip());
            String note = cols.length == 5 ? cols[4].strip() : "";
            int before = problems.size();
            if (query.isEmpty()) {
                problems.add("linha " + lineNo + ": object_query vazio");
            }
            if (provider == null) {
                problems.add("linha " + lineNo + ": provider '" + cols[1].strip() + "' (use HUBBLE ou WEBB)");
            }
            if (!EsaSite.IMAGE_ID.matcher(imageId).matches()) {
                problems.add("linha " + lineNo + ": image_id '" + imageId + "' fora do formato");
            }
            if (action == null) {
                problems.add("linha " + lineNo + ": action '" + cols[3].strip() + "' (use include, hero ou exclude)");
            }
            UUID objectId = query.isEmpty() ? null : resolveObject.apply(query);
            if (!query.isEmpty() && objectId == null) {
                problems.add("linha " + lineNo + ": objeto '" + query + "' não encontrado (ou ambíguo) no catálogo local");
            }
            if (problems.size() == before) {
                entries.add(new CurationEntry(lineNo, query, objectId, provider, imageId, action, note));
            }
        }
        if (!headerSeen && !entries.isEmpty()) {
            problems.add("arquivo sem cabeçalho");
        }
        if (!problems.isEmpty()) {
            throw new InvalidCurationException(problems);
        }
        return new CurationFile(entries);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
