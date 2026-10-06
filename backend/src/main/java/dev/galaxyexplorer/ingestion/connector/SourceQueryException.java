package dev.galaxyexplorer.ingestion.connector;

/** Falha permanente (4xx, erro de ADQL, resposta em formato inesperado, truncamento): NAO fazer retry. */
public class SourceQueryException extends RuntimeException {

    public SourceQueryException(String message) {
        super(message);
    }

    public SourceQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
