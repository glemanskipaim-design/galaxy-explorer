package dev.galaxyexplorer.ingestion.pipeline;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.catalog.source.SourceRepository;
import dev.galaxyexplorer.catalog.source.SourceRow;
import dev.galaxyexplorer.catalog.write.CatalogWriter;
import dev.galaxyexplorer.catalog.write.WriteResult;
import dev.galaxyexplorer.ingestion.connector.FetchedBatch;
import dev.galaxyexplorer.ingestion.connector.NormalizationOutcome;
import dev.galaxyexplorer.ingestion.connector.NormalizedObject;
import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.Resolution;
import dev.galaxyexplorer.ingestion.connector.SchemaCheck;
import dev.galaxyexplorer.ingestion.connector.SourceConnector;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import dev.galaxyexplorer.ingestion.connector.UnresolvedQuery;
import dev.galaxyexplorer.ingestion.pipeline.ObjectOutcome.Kind;

/**
 * Pipeline de ingestao (secao 8 do doc da Fase 0), generico para qualquer
 * {@link SourceConnector}. E o UNICO dono da fronteira transacional da
 * ingestao:
 * <ul>
 *   <li>run, payloads crus e fechamento: autocommit (existem em disco mesmo se
 *       o resto falhar);</li>
 *   <li>cada objeto: uma transacao propria ({@link TransactionTemplate}) —
 *       excecao num objeto desfaz so ele, o run segue.</li>
 * </ul>
 */
@Service
public class IngestionPipeline {

    static final int MAX_ERROR_SUMMARY = 4000;

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final Map<SourceCode, SourceConnector> connectors = new EnumMap<>(SourceCode.class);
    private final IngestionRunRepository runs;
    private final RawPayloadRepository rawPayloads;
    private final SourceRepository sources;
    private final CatalogWriter writer;
    private final TransactionTemplate tx;
    private final IngestionProperties props;

    public IngestionPipeline(List<SourceConnector> connectors, IngestionRunRepository runs,
                             RawPayloadRepository rawPayloads, SourceRepository sources, CatalogWriter writer,
                             TransactionTemplate tx, IngestionProperties props) {
        connectors.forEach(c -> this.connectors.put(c.code(), c));
        this.runs = runs;
        this.rawPayloads = rawPayloads;
        this.sources = sources;
        this.writer = writer;
        this.tx = tx;
        this.props = props;
    }

    public IngestionReport run(IngestionRequest request) {
        SourceConnector connector = connectors.get(request.source());
        if (connector == null) {
            throw new IllegalArgumentException("nenhum conector registrado para " + request.source());
        }
        SourceRow source = sources.getByCode(request.source());
        // 1. run aberto e ja commitado
        long runId = runs.open(source.id(), request.trigger(), connector.connectorVersion());
        List<ObjectOutcome> outcomes = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        List<String> queries = new ArrayList<>(new LinkedHashSet<>(
                request.queries().stream().map(String::trim).filter(q -> !q.isEmpty()).toList()));
        try {
            // 2. esquema da fonte
            SchemaCheck schema = connector.checkSchema();
            rawPayloads.insert(runId, schema.raw());
            if (!schema.ok()) {
                issues.addAll(schema.problems());
                queries.forEach(q -> outcomes.add(ObjectOutcome.failed(q, Kind.SCHEMA_MISMATCH,
                        "esquema da fonte divergente: " + String.join("; ", schema.problems()))));
                return close(runId, outcomes, issues);
            }
            // 3-6. lotes
            List<List<String>> batches = partition(queries, props.batchSize());
            for (int i = 0; i < batches.size(); i++) {
                List<String> batch = batches.get(i);
                try {
                    runBatch(connector, runId, batch, outcomes, issues);
                } catch (SourceUnavailableException e) {
                    String reason = "fonte indisponível: " + e.getMessage();
                    log.warn("Run {}: fonte indisponível — {} lote(s) restante(s) contados como falha", runId,
                            batches.size() - i, e);
                    for (List<String> remaining : batches.subList(i, batches.size())) {
                        failUnanswered(remaining, outcomes, Kind.SOURCE_UNAVAILABLE, reason);
                    }
                    break;
                } catch (SourceQueryException e) {
                    log.warn("Run {}: lote falhou", runId, e);
                    failUnanswered(batch, outcomes, Kind.SOURCE_QUERY_FAILED,
                            "falha na consulta à fonte: " + e.getMessage());
                }
            }
            return close(runId, outcomes, issues);
        } catch (RuntimeException e) {
            log.error("Run {} abortado por erro inesperado", runId, e);
            issues.add("erro inesperado: " + e);
            failUnanswered(queries, outcomes, Kind.INTERNAL_ERROR, "erro inesperado no pipeline: " + e.getMessage());
            return close(runId, outcomes, issues);
        }
    }

    private void runBatch(SourceConnector connector, long runId, List<String> batch, List<ObjectOutcome> outcomes,
                          List<String> issues) {
        Resolution resolution = connector.resolve(batch);
        rawPayloads.insert(runId, resolution.raw());
        for (UnresolvedQuery u : resolution.unresolved()) {
            outcomes.add(ObjectOutcome.notFound(u.query(), u.reason()));
        }
        if (resolution.resolved().isEmpty()) {
            return;
        }
        FetchedBatch fetched = connector.fetch(resolution.resolved());
        Map<String, StoredPayload> stored = new HashMap<>();
        for (RawPayload payload : fetched.payloadsByDataset().values()) {
            stored.put(payload.dataset(), new StoredPayload(rawPayloads.insert(runId, payload),
                    payload.retrievedAt()));
        }
        for (NormalizationOutcome outcome : connector.normalize(fetched)) {
            if (outcome instanceof NormalizationOutcome.Accepted accepted) {
                writeObject(accepted.object(), stored, runId, outcomes, issues);
            } else if (outcome instanceof NormalizationOutcome.Rejected rejected) {
                rejected.name().queries().forEach(q -> outcomes.add(ObjectOutcome.failed(q, Kind.REJECTED,
                        "rejeitado na validação: " + rejected.reason())));
            }
        }
    }

    private void writeObject(NormalizedObject object, Map<String, StoredPayload> stored, long runId,
                             List<ObjectOutcome> outcomes, List<String> issues) {
        try {
            WriteResult result = tx.execute(status -> writer.write(NormalizedToCatalog.toWrite(object, stored, runId)));
            object.queries().forEach(q -> outcomes.add(ObjectOutcome.written(q, result.objectId(), result.slug(),
                    result.created())));
            issues.addAll(object.issues());
        } catch (RuntimeException e) {
            log.warn("Run {}: falha ao gravar {} ({})", runId, object.mainId(), object.ref().sourceRef(), e);
            object.queries().forEach(q -> outcomes.add(ObjectOutcome.failed(q, Kind.WRITE_FAILED,
                    "falha ao gravar: " + e.getMessage())));
        }
    }

    /** Marca como falha as consultas do lote que ainda nao tem desfecho. */
    private static void failUnanswered(List<String> batch, List<ObjectOutcome> outcomes, Kind kind, String reason) {
        for (String q : batch) {
            boolean answered = outcomes.stream().anyMatch(o -> o.query().equals(q));
            if (!answered) {
                outcomes.add(ObjectOutcome.failed(q, kind, reason));
            }
        }
    }

    private IngestionReport close(long runId, List<ObjectOutcome> outcomes, List<String> issues) {
        int ok = (int) outcomes.stream().filter(ObjectOutcome::ok).count();
        int failed = outcomes.size() - ok;
        RunStatus status = failed == 0 ? RunStatus.OK : ok == 0 ? RunStatus.FAILED : RunStatus.PARTIAL;
        List<String> summary = new ArrayList<>();
        outcomes.stream().filter(o -> !o.ok()).forEach(o -> summary.add(o.query() + ": " + o.failureReason()));
        summary.addAll(issues);
        runs.close(runId, status, ok, failed, summary.isEmpty() ? null : truncate(String.join("\n", summary)));
        return new IngestionReport(runId, status, ok, failed, outcomes, issues);
    }

    static String truncate(String s) {
        return s.length() <= MAX_ERROR_SUMMARY ? s : s.substring(0, MAX_ERROR_SUMMARY - 3) + "...";
    }

    static <T> List<List<T>> partition(List<T> items, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            out.add(List.copyOf(items.subList(i, Math.min(items.size(), i + size))));
        }
        return out;
    }
}
