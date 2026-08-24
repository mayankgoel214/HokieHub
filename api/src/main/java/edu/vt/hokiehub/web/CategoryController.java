package edu.vt.hokiehub.web;

import edu.vt.hokiehub.service.CategoryService;
import edu.vt.hokiehub.web.dto.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "The two-level category tree")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "The full category tree", description = "Cached; changes rarely.")
    public List<CategoryResponse> tree() {
        return service.tree();
    }
}
