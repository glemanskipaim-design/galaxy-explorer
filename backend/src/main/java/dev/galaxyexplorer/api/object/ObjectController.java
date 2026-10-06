package dev.galaxyexplorer.api.object;

import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.galaxyexplorer.api.error.ApiErrorCode;
import dev.galaxyexplorer.api.error.ApiException;
import dev.galaxyexplorer.catalog.read.ObjectQueryService;

/**
 * Detalhe de um objeto.
 *
 * <p>{@code GET /api/v1/objects/{slug}}
 * <ul>
 *   <li>200: {@link ObjectDetailResponse} — cada valor com fonte, referencia (bibcode + link ADS),
 *       natureza epistemica e data de coleta; propriedades ausentes com {@code available:false} e motivo;
 *       {@code sources} traz o credito exigido de cada fonte usada;</li>
 *   <li>404 {@code OBJECT_NOT_FOUND}: slug inexistente ou fora do formato {@code ^[a-z0-9-]{1,120}$}
 *       (formato invalido nem consulta o banco).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/objects")
public class ObjectController {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9-]{1,120}$");

    private final ObjectQueryService objects;

    public ObjectController(ObjectQueryService objects) {
        this.objects = objects;
    }

    @GetMapping("/{slug}")
    public ObjectDetailResponse get(@PathVariable("slug") String slug) {
        if (!SLUG.matcher(slug).matches()) {
            throw notFound();
        }
        return objects.findBySlug(slug).map(ObjectDetailMapper::from).orElseThrow(ObjectController::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(ApiErrorCode.OBJECT_NOT_FOUND, "objeto não encontrado no catálogo local", null);
    }
}
