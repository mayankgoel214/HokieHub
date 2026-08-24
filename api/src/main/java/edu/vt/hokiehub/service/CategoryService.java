package edu.vt.hokiehub.service;

import edu.vt.hokiehub.domain.Category;
import edu.vt.hokiehub.repository.CategoryRepository;
import edu.vt.hokiehub.web.dto.CategoryResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    /**
     * The category tree is read on nearly every page and changes almost never, which
     * makes it the one query in this application genuinely worth caching. Mapping to
     * DTOs happens inside the transaction so the cached value holds no lazy proxies.
     */
    @Cacheable("categoryTree")
    @Transactional(readOnly = true)
    public List<CategoryResponse> tree() {
        return categories.findTopLevelWithChildren().stream()
                .map(CategoryResponse::withChildren)
                .toList();
    }

    @CacheEvict(value = "categoryTree", allEntries = true)
    public void evictTree() {
        // Called when categories change. Separate method so the eviction point is explicit.
    }

    @Transactional(readOnly = true)
    public List<Category> all() {
        return categories.findAll();
    }
}
