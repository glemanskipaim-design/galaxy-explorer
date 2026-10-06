package dev.galaxyexplorer.ingestion.connector;

import java.util.List;

import dev.galaxyexplorer.catalog.SourceCode;

/**
 * Contrato de um conector de fonte (secao 5.1 do doc da Fase 0, adaptado para
 * lote: a secao 8 exige consultas via {@code IN (...)}).
 */
public interface SourceConnector {

    SourceCode code();

    /** Gravado em {@code ingestion_run.connector_version}; incrementar quando o mapper mudar de comportamento. */
    String connectorVersion();

    /** Uma consulta de metadados por run: as colunas esperadas existem? */
    SchemaCheck checkSchema();

    /** Nomes do usuario -> refs da fonte (uma requisicao para o lote). */
    Resolution resolve(List<String> userQueries);

    /** Busca os dados dos objetos resolvidos; um {@link RawPayload} por consulta. */
    FetchedBatch fetch(List<ResolvedName> resolved);

    /** PURO: sem rede, sem banco, sem relogio. */
    List<NormalizationOutcome> normalize(FetchedBatch batch);
}
