package dev.galaxyexplorer.ingestion.connector.tap;

import dev.galaxyexplorer.ingestion.connector.RawPayload;

/** Cliente TAP sincrono generico (SIMBAD, NASA Exoplanet Archive...). */
public interface TapClient {

    /**
     * Executa uma consulta ADQL e devolve a resposta JSON crua.
     *
     * @param dataset rotulo gravado em {@code raw_payload.dataset} ({@code 'basic'}, {@code 'ident'}...)
     */
    RawPayload query(String dataset, String adql);
}
