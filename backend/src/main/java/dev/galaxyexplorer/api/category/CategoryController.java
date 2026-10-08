package dev.galaxyexplorer.api.category;

import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.galaxyexplorer.api.error.ApiErrorCode;
import dev.galaxyexplorer.api.error.ApiException;
import dev.galaxyexplorer.api.image.ImageDtoMapper;
import dev.galaxyexplorer.catalog.category.CategoryRow;
import dev.galaxyexplorer.catalog.read.CategoryPage;
import dev.galaxyexplorer.catalog.read.CategoryQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Navegacao por categoria.
 *
 * <p>{@code GET /api/v1/categories}: 200 {@link CategoryListResponse} — so categorias raiz, todas
 * (inclusive com 0 objetos), ordenadas por nome em portugues; {@code objectCount} conta a subarvore.
 *
 * <p>{@code GET /api/v1/categories/{code}/objects?limit=24&offset=0}
 * <ul>
 *   <li>200 {@link CategoryObjectsResponse}: objetos da subarvore de {@code code} (raiz ou
 *       subcategoria), em ordem natural de nome ({@code M 2} antes de {@code M 10});</li>
 *   <li>400 {@code VALIDATION_FAILED}: {@code limit} fora de 1..100 ou {@code offset} fora de 0..10000;</li>
 *   <li>404 {@code CATEGORY_NOT_FOUND}: categoria inexistente ou fora do formato {@code ^[a-z_]{1,40}$}
 *       (formato invalido nem consulta o banco).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private static final Pattern CODE = Pattern.compile("^[a-z_]{1,40}$");

    private final CategoryQueryService categories;

    public CategoryController(CategoryQueryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public CategoryListResponse list() {
        return new CategoryListResponse(categories.roots().stream()
                .map(c -> new CategoryListResponse.Item(c.code(), c.namePt(), c.nameEn(), c.objectCount()))
                .toList());
    }

    @GetMapping("/{code}/objects")
    public CategoryObjectsResponse objects(
            @PathVariable("code") String code,
            @RequestParam(name = "limit", defaultValue = "24") @Min(1) @Max(100) int limit,
            @RequestParam(name = "offset", defaultValue = "0") @Min(0) @Max(10000) int offset) {
        if (!CODE.matcher(code).matches()) {
            throw notFound();
        }
        return categories.objectsOf(code, limit, offset).map(CategoryController::toResponse)
                .orElseThrow(CategoryController::notFound);
    }

    private static CategoryObjectsResponse toResponse(CategoryPage page) {
        CategoryRow c = page.category();
        return new CategoryObjectsResponse(new CategoryObjectsResponse.Category(c.code(), c.namePt(), c.nameEn()),
                page.total(), page.limit(), page.offset(), page.items().stream()
                        .map(CategoryController::toItem)
                        .toList());
    }

    private static CategoryObjectsResponse.Item toItem(CategoryPage.Item item) {
        CategoryRow category = item.category();
        return new CategoryObjectsResponse.Item(item.object().slug(), item.object().displayName(),
                category == null ? null : category.code(), category == null ? null : category.namePt(),
                ImageDtoMapper.thumbnail(item.thumbnail()));
    }

    private static ApiException notFound() {
        return new ApiException(ApiErrorCode.CATEGORY_NOT_FOUND, "categoria não encontrada", null);
    }
}
