package dev.galaxyexplorer.catalog.read;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import dev.galaxyexplorer.catalog.category.CategoryCount;
import dev.galaxyexplorer.catalog.category.CategoryRepository;
import dev.galaxyexplorer.catalog.category.CategoryRow;
import dev.galaxyexplorer.catalog.object.CelestialObjectRepository;
import dev.galaxyexplorer.catalog.object.ObjectRow;

/**
 * Leitura do catalogo por categoria: contagem por raiz (atalhos da home) e
 * listagem paginada da subarvore de uma categoria.
 */
@Service
public class CategoryQueryService {

    private final CategoryRepository categories;
    private final CelestialObjectRepository objects;

    public CategoryQueryService(CategoryRepository categories, CelestialObjectRepository objects) {
        this.categories = categories;
        this.objects = objects;
    }

    public List<CategoryCount> roots() {
        return categories.findRootsWithCounts();
    }

    /** @return vazio se a categoria nao existe */
    public Optional<CategoryPage> objectsOf(String code, int limit, int offset) {
        return categories.findByCode(code).map(category -> page(category, limit, offset));
    }

    private CategoryPage page(CategoryRow category, int limit, int offset) {
        List<Integer> ids = categories.findSubtreeIds(category.id());
        long total = objects.countByCategoryIds(ids);
        List<ObjectRow> rows = objects.findByCategoryIds(ids, limit, offset);
        // A subarvore tem poucas categorias: cache montado uma vez, nunca uma consulta por item.
        Map<Integer, Optional<CategoryRow>> categoryCache = new HashMap<>();
        List<CategoryPage.Item> items = rows.stream()
                .map(row -> new CategoryPage.Item(row,
                        categoryCache.computeIfAbsent(row.categoryId(), categories::findById).orElse(null)))
                .toList();
        return new CategoryPage(category, total, limit, offset, items);
    }
}
