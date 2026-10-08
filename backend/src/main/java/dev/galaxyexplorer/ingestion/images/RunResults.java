package dev.galaxyexplorer.ingestion.images;

import java.util.ArrayList;
import java.util.List;

import dev.galaxyexplorer.ingestion.pipeline.IngestionRunRepository;
import dev.galaxyexplorer.ingestion.pipeline.RunStatus;

/**
 * Fechamento de um run de imagens: {@code records_ok} = objetos com imagem gravada, {@code records_failed} =
 * objetos com falha de fonte/gravacao; "sem posicao"/"sem candidata valida"/"inalterado" nao sao falha.
 * O {@code error_summary} lista falhas, rejeicoes e avisos (truncado como o do pipeline).
 */
final class RunResults {

    static final int MAX_ERROR_SUMMARY = 4000;

    private RunResults() {
    }

    static ImageRunResult close(IngestionRunRepository runs, long runId, ImageSource source,
                                 List<ImageOutcome> outcomes, List<String> notes) {
        int ok = (int) outcomes.stream().filter(o -> o.kind() == ImageOutcome.Kind.STORED).count();
        int failed = (int) outcomes.stream().filter(o -> o.kind().failure()).count();
        RunStatus status = failed == 0 ? RunStatus.OK : ok == 0 ? RunStatus.FAILED : RunStatus.PARTIAL;
        List<String> summary = new ArrayList<>();
        outcomes.stream()
                .filter(o -> o.kind() != ImageOutcome.Kind.STORED && o.kind() != ImageOutcome.Kind.UNCHANGED)
                .forEach(o -> summary.add((o.slug() == null ? "?" : o.slug()) + " [" + o.kind() + "]: " + o.detail()));
        summary.addAll(notes);
        runs.close(runId, status, ok, failed, summary.isEmpty() ? null : truncate(String.join("\n", summary)));
        return new ImageRunResult(runId, status, outcomes, notes);
    }

    static String truncate(String s) {
        return s.length() <= MAX_ERROR_SUMMARY ? s : s.substring(0, MAX_ERROR_SUMMARY - 3) + "...";
    }
}
