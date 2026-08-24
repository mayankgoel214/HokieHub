package edu.vt.hokiehub.repository;

import edu.vt.hokiehub.domain.Listing;
import edu.vt.hokiehub.domain.ListingStatus;
import edu.vt.hokiehub.domain.ListingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    /**
     * Seller and category are fetched in the same query. Without the entity graph a
     * page of 20 listings issues 41 queries — one for the page and two per row — which
     * is the single worst performance trap in this domain.
     */
    @EntityGraph(attributePaths = {"seller", "category"})
    @Query("select l from Listing l")
    Page<Listing> findAllWithSellerAndCategory(Pageable pageable);

    @EntityGraph(attributePaths = {"seller", "category", "images", "serviceDetail"})
    Optional<Listing> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"seller", "category"})
    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"seller", "category"})
    Page<Listing> findBySellerId(String sellerId, Pageable pageable);

    /**
     * Category filtering follows the tree one level down, so browsing "Electronics"
     * also returns everything filed under "Laptops & Computers".
     */
    @EntityGraph(attributePaths = {"seller", "category"})
    @Query("""
           select l from Listing l
           where (l.category.id = :categoryId or l.category.parent.id = :categoryId)
             and (:status is null or l.status = :status)
             and (:listingType is null or l.listingType = :listingType)
             and (:minPrice is null or l.price >= :minPrice)
             and (:maxPrice is null or l.price <= :maxPrice)
           """)
    Page<Listing> search(@Param("categoryId") Integer categoryId,
                         @Param("status") ListingStatus status,
                         @Param("listingType") ListingType listingType,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         Pageable pageable);

    /**
     * A view is a counter bump, not a read-modify-write of the whole row: doing it in
     * the database avoids losing counts when two people open a listing at once.
     */
    @Modifying
    @Query("update Listing l set l.viewsCount = l.viewsCount + 1 where l.id = :id")
    void incrementViews(@Param("id") UUID id);
}
