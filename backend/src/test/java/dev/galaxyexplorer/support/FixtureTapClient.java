package dev.galaxyexplorer.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.tap.TapClient;

/**
 * {@link TapClient} de teste: devolve a resposta REAL gravada cuja consulta
 * {@code .adql} e igual (whitespace normalizado) a pedida. Nunca gera resposta:
 * consulta sem fixture falha com a ADQL completa na mensagem — exatamente o
 * texto a salvar e capturar com curl.
 *
 * <p>Injecao de falha para testes de ramos de erro: {@link #failOn} lanca uma
 * excecao na N-esima consulta de um dataset; {@link #transformOn} altera EM
 * MEMORIA o corpo real de um dataset (ex. trocar uma unidade). Nenhum dos dois
 * inventa resposta da fonte.
 */
public class FixtureTapClient implements TapClient {

    private final Map<String, String> bodyByAdql = new HashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final Map<String, AtomicInteger> callsByDataset = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, Supplier<RuntimeException>>> faults = new ConcurrentHashMap<>();
    private final Map<String, UnaryOperator<String>> transforms = new ConcurrentHashMap<>();

    public FixtureTapClient() {
        try {
            Resource[] adqls = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:fixtures/simbad/*.adql");
            for (Resource adql : adqls) {
                String name = adql.getFilename().replace(".adql", "");
                Resource json = adql.createRelative(name + ".json");
                if (json.exists()) {
                    bodyByAdql.put(normalize(adql.getContentAsString(StandardCharsets.UTF_8)),
                            json.getContentAsString(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public RawPayload query(String dataset, String adql) {
        calls.incrementAndGet();
        int n = callsByDataset.computeIfAbsent(dataset, k -> new AtomicInteger()).incrementAndGet();
        Supplier<RuntimeException> fault = faults.getOrDefault(dataset, Map.of()).get(n);
        if (fault != null) {
            throw fault.get();
        }
        String body = bodyByAdql.get(normalize(adql));
        if (body == null) {
            throw new AssertionError("Sem fixture para a consulta (capture-a com curl e salve como .adql/.json):\n"
                    + adql);
        }
        body = transforms.getOrDefault(dataset, UnaryOperator.identity()).apply(body);
        return RawPayload.of(dataset, Fixtures.SIMBAD_TAP_URL, adql, body, RawPayload.BodyFormat.JSON,
                Fixtures.CAPTURED_AT);
    }

    public int callCount() {
        return calls.get();
    }

    /** Lanca {@code fault} na {@code nth} consulta (1 = primeira) do {@code dataset}. */
    public FixtureTapClient failOn(String dataset, int nth, Supplier<RuntimeException> fault) {
        faults.computeIfAbsent(dataset, k -> new ConcurrentHashMap<>()).put(nth, fault);
        return this;
    }

    /** Aplica {@code change} ao corpo REAL de todas as respostas do {@code dataset}. */
    public FixtureTapClient transformOn(String dataset, UnaryOperator<String> change) {
        transforms.put(dataset, change);
        return this;
    }

    /** Zera contadores e remove falhas/transformacoes injetadas. */
    public void reset() {
        calls.set(0);
        callsByDataset.clear();
        faults.clear();
        transforms.clear();
    }

    static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }
}
