package dev.galaxyexplorer.ingestion.connector;

/** Falha transitoria (rede, 5xx, 429, circuito aberto): o retry atua. */
public class SourceUnavailableException extends RuntimeException {

    public SourceUnavailableException(String message) {
        super(message);
    }

    public SourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
