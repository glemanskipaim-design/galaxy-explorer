package dev.galaxyexplorer.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.simbad.SesameClient;
import dev.galaxyexplorer.ingestion.connector.simbad.SesameUrls;

/**
 * {@link SesameClient} de teste: devolve a resposta REAL gravada
 * ({@code fixtures/sesame/<nome>.xml}) cuja lista de nomes
 * ({@code <nome>.names}, um por linha) e igual a pedida. Nunca gera resposta.
 */
public class FixtureSesameClient implements SesameClient {

    public static final URI SESAME_URL = URI.create("https://cds.unistra.fr/cgi-bin/nph-sesame/-oxp/S");

    private final Map<List<String>, String> xmlByNames = new HashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final Map<Integer, Supplier<RuntimeException>> faults = new ConcurrentHashMap<>();

    public FixtureSesameClient() {
        try {
            Resource[] lists = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:fixtures/sesame/*.names");
            for (Resource list : lists) {
                String name = list.getFilename().replace(".names", "");
                List<String> names = Arrays.stream(list.getContentAsString(StandardCharsets.UTF_8).split("\\R"))
                        .filter(s -> !s.isBlank())
                        .toList();
                xmlByNames.put(names, list.createRelative(name + ".xml").getContentAsString(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public RawPayload resolve(List<String> names) {
        Supplier<RuntimeException> fault = faults.get(calls.incrementAndGet());
        if (fault != null) {
            throw fault.get();
        }
        String xml = xmlByNames.get(List.copyOf(names));
        if (xml == null) {
            throw new AssertionError("Sem fixture Sesame para os nomes (capture com curl e salve .names/.xml): "
                    + names);
        }
        return RawPayload.of(DATASET, SesameUrls.build(SESAME_URL, names).toString(), String.join("\n", names), xml,
                RawPayload.BodyFormat.TEXT, Fixtures.CAPTURED_AT);
    }

    public int callCount() {
        return calls.get();
    }

    /** Lanca {@code fault} na {@code nth} chamada (1 = primeira). Nao gera resposta de fonte. */
    public FixtureSesameClient failOnCall(int nth, Supplier<RuntimeException> fault) {
        faults.put(nth, fault);
        return this;
    }

    /** Zera o contador e remove falhas injetadas. */
    public void reset() {
        calls.set(0);
        faults.clear();
    }
}
