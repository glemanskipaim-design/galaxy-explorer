package dev.galaxyexplorer.api.error;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import dev.galaxyexplorer.ingestion.jobs.ImportQueueFullException;
import jakarta.validation.ConstraintViolationException;

/** Traduz excecoes para o formato padrao. Nunca devolve stack trace no corpo. */
@RestControllerAdvice
public class ApiExceptionHandler {

    static final String RETRY_AFTER_SECONDS = "30";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return respond(e.code(), e.getMessage(), e.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        List<Map<String, String>> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        return respond(ApiErrorCode.VALIDATION_FAILED, "corpo da requisição inválido", details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> invalidParams(HandlerMethodValidationException e) {
        List<Map<String, String>> details = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(err -> Map.of(
                        "field", String.valueOf(r.getMethodParameter().getParameterName()),
                        "message", String.valueOf(err.getDefaultMessage()))))
                .toList();
        return respond(ApiErrorCode.VALIDATION_FAILED, "parâmetros inválidos", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException e) {
        List<Map<String, String>> details = e.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();
        return respond(ApiErrorCode.VALIDATION_FAILED, "parâmetros inválidos", details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missingParam(MissingServletRequestParameterException e) {
        return respond(ApiErrorCode.VALIDATION_FAILED, "parâmetro obrigatório ausente",
                List.of(Map.of("field", e.getParameterName(), "message", "obrigatório")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e) {
        return respond(ApiErrorCode.VALIDATION_FAILED, "parâmetro com tipo inválido",
                List.of(Map.of("field", e.getName(), "message", "tipo inválido")));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiError> unreadable(Exception e) {
        return respond(ApiErrorCode.VALIDATION_FAILED, "corpo da requisição ilegível ou em formato não suportado",
                null);
    }

    @ExceptionHandler(ImportQueueFullException.class)
    ResponseEntity<ApiError> queueFull(ImportQueueFullException e) {
        return ResponseEntity.status(ApiErrorCode.IMPORT_QUEUE_FULL.status())
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(ApiError.of(ApiErrorCode.IMPORT_QUEUE_FULL, e.getMessage(), null));
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ApiError> notFound(Exception e) throws Exception {
        throw e; // deixa o Spring responder 404/405 como de costume
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Erro nao tratado na API", e);
        return respond(ApiErrorCode.INTERNAL_ERROR, "erro interno", null);
    }

    private static ResponseEntity<ApiError> respond(ApiErrorCode code, String message, Object details) {
        return ResponseEntity.status(code.status()).body(ApiError.of(code, message, details));
    }
}
