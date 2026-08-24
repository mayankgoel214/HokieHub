package edu.vt.hokiehub.web.dto;

import edu.vt.hokiehub.domain.Category;
import java.util.List;

public record CategoryResponse(Integer id, String name, String description,
                               String icon, List<CategoryResponse> children) {

    public static CategoryResponse withChildren(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getIcon(),
                c.getChildren().stream()
                        .map(CategoryResponse::leaf)
                        .sorted((a, b) -> a.name().compareTo(b.name()))
                        .toList());
    }

    public static CategoryResponse leaf(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getIcon(), List.of());
    }
}
