package dev.galaxyexplorer.api.imports;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.galaxyexplorer.api.error.ApiErrorCode;
import dev.galaxyexplorer.api.error.ApiException;
import dev.galaxyexplorer.catalog.read.ObjectQueryService;
import dev.galaxyexplorer.ingestion.jobs.ImportJob;
import dev.galaxyexplorer.ingestion.jobs.ImportJobService;
import jakarta.validation.Valid;

/**
 * Importacao sob demanda (termo sem resultado local).
 *
 * <p>{@code POST /api/v1/imports} com {@code {"query": "..."}}
 * <ul>
 *   <li>202 + {@code Location: /api/v1/imports/{id}} + {@link ImportJobResponse}; se ja existe job
 *       ativo para o mesmo termo, 202 com o job existente;</li>
 *   <li>400 {@code VALIDATION_FAILED}: corpo invalido (vazio, &gt; 100 caracteres, charset);</li>
 *   <li>429 {@code IMPORT_QUEUE_FULL} + {@code Retry-After}: fila cheia.</li>
 * </ul>
 *
 * <p>{@code GET /api/v1/imports/{id}}: 200 {@link ImportJobResponse} (para polling) ou
 * 404 {@code IMPORT_NOT_FOUND}.
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportJobService imports;
    private final ObjectQueryService objects;

    public ImportController(ImportJobService imports, ObjectQueryService objects) {
        this.imports = imports;
        this.objects = objects;
    }

    @PostMapping
    public ResponseEntity<ImportJobResponse> create(@Valid @RequestBody CreateImportRequest request) {
        ImportJob job = imports.submit(request.query());
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/imports/" + job.id()))
                .body(toResponse(job));
    }

    @GetMapping("/{id}")
    public ImportJobResponse get(@PathVariable("id") long id) {
        return imports.find(id).map(this::toResponse)
                .orElseThrow(() -> new ApiException(ApiErrorCode.IMPORT_NOT_FOUND, "importação não encontrada",
                        null));
    }

    private ImportJobResponse toResponse(ImportJob job) {
        return new ImportJobResponse(job.id(), job.query(), job.status().dbValue(), job.message(),
                objects.slugsOf(job.resultObjectIds()), job.runId(), job.createdAt(), job.startedAt(),
                job.finishedAt());
    }
}
