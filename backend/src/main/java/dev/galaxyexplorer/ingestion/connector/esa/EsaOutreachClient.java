package dev.galaxyexplorer.ingestion.connector.esa;

import dev.galaxyexplorer.ingestion.connector.RawPayload;

/** Busca e AVM dos sites de divulgacao da ESA. */
public interface EsaOutreachClient {

    String DATASET_SEARCH = "esa_search_html";
    String DATASET_AVM = "esa_avm_json";

    /** HTML exato da busca por {@code Subject.Name} (dataset {@value #DATASET_SEARCH}, TEXT). */
    RawPayload search(EsaSite site, String subjectTerm);

    /**
     * JSON AVM exato (dataset {@value #DATASET_AVM}). O servico responde {@code text/plain}; se o corpo nao
     * for um objeto JSON, volta como TEXT (e a candidata e rejeitada adiante).
     */
    RawPayload avm(EsaSite site, String imageId);
}
