package dev.galaxyexplorer.api.error;

/** Erro de negocio que a API traduz direto para {@link ApiError}. */
public class ApiException extends RuntimeException {

    private final ApiErrorCode code;
    private final transient Object details;

    /** @param details pode ser {@code null} */
    public ApiException(ApiErrorCode code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ApiErrorCode code() {
        return code;
    }

    public Object details() {
        return details;
    }
}
