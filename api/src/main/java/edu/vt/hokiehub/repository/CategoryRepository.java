package edu.vt.hokiehub.repository;

import edu.vt.hokiehub.domain.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    /** The whole two-level tree in one query, for the category picker. */
    @EntityGraph(attributePaths = {"children"})
    @Query("select c from Category c where c.parent is null order by c.name")
    List<Category> findTopLevelWithChildren();

    Optional<Category> findByName(String name);
}
