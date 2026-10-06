package dev.galaxyexplorer.ingestion.connector;

import java.util.List;

/** Resultado da checagem de esquema da fonte no inicio de um run. */
public record SchemaCheck(boolean ok, List<String> problems, RawPayload raw) {

    public SchemaCheck {
        problems = List.copyOf(problems);
    }
}
