package dev.galaxyexplorer.ingestion.images;

import java.util.List;
import java.util.Map;

import dev.galaxyexplorer.ingestion.pipeline.RunStatus;

/**
 * @param runIds          run aberto por fonte
 * @param runStatuses     status final de cada run
 * @param cutoutOnlySlugs objetos pedidos que, depois desta execucao, so tem recorte de survey (nenhuma
 *                        imagem ESA valida) — a lista real "so recorte"
 * @param issues          avisos gerais (ex. busca da ESA vazia para todos os alvos)
 */
public record ImageIngestionReport(Map<ImageSource, Long> runIds, Map<ImageSource, RunStatus> runStatuses,
                                   List<ImageOutcome> outcomes, List<String> cutoutOnlySlugs, List<String> issues) {

    public ImageIngestionReport {
        runIds = Map.copyOf(runIds);
        runStatuses = Map.copyOf(runStatuses);
        outcomes = List.copyOf(outcomes);
        cutoutOnlySlugs = List.copyOf(cutoutOnlySlugs);
        issues = List.copyOf(issues);
    }

    public boolean anyRunFailed() {
        return runStatuses.containsValue(RunStatus.FAILED);
    }
}
