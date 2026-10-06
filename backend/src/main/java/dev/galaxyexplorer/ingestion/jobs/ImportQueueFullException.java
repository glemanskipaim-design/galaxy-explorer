package dev.galaxyexplorer.ingestion.jobs;

/** A fila de importacoes esta cheia; o cliente deve tentar de novo mais tarde. */
public class ImportQueueFullException extends RuntimeException {

    public ImportQueueFullException(String message) {
        super(message);
    }
}
