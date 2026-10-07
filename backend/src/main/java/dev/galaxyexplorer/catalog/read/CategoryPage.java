package dev.galaxyexplorer.catalog.read;

import java.util.List;

import dev.galaxyexplorer.catalog.category.CategoryRow;
import dev.galaxyexplorer.catalog.object.ObjectRow;

/**
 * Uma pagina dos objetos da subarvore de uma categoria.
 *
 * @param category categoria pedida (raiz ou subcategoria)
 * @param total    total de objetos na subarvore (independe da paginacao)
 */
public record CategoryPage(CategoryRow category, long total, int limit, int offset, List<Item> items) {

    /** @param category categoria do proprio objeto (pode ser uma filha da pedida) */
    public record Item(ObjectRow object, CategoryRow category) {
    }
}
