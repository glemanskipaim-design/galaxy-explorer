package dev.galaxyexplorer.api.error;

/**
 * Corpo de erro padrao: {@code {"error":{"code","message","details"}}}.
 * {@code details} sempre presente no JSON ({@code null} quando vazio).
 */
public record ApiError(Body error) {

    public record Body(String code, String message, Object details) {
    }

    public static ApiError of(ApiErrorCode code, String message, Object details) {
        return new ApiError(new Body(code.name(), message, details));
    }
}
