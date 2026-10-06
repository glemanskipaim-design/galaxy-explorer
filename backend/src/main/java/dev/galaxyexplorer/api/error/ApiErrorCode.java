package dev.galaxyexplorer.api.error;

import org.springframework.http.HttpStatus;

/** Catalogo central de codigos de erro da API. */
public enum ApiErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    OBJECT_NOT_FOUND(HttpStatus.NOT_FOUND),
    IMPORT_NOT_FOUND(HttpStatus.NOT_FOUND),
    IMPORT_QUEUE_FULL(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ApiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
