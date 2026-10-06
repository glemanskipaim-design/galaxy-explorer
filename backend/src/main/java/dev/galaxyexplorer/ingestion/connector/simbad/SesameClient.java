package dev.galaxyexplorer.ingestion.connector.simbad;

import java.util.List;

import dev.galaxyexplorer.ingestion.connector.RawPayload;

/**
 * Resolvedor de nomes do CDS (Sesame), restrito ao SIMBAD (opcao {@code S}).
 * Uma requisicao resolve varios nomes: a resposta tem um {@code <Target>} por
 * nome, na mesma ordem.
 */
public interface SesameClient {

    String DATASET = "sesame";

    /** @return resposta crua (XML como texto) */
    RawPayload resolve(List<String> names);
}
