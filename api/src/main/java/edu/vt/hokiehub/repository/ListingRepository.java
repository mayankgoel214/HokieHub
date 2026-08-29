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
     * Images but not defects, deliberately. Both are Lists, and Hibernate refuses
     * to fetch two bags in one query — MultipleBagFetchException — because the
     * cartesian product of two collections cannot be unpicked back into rows. The
     * service initialises defects separately, inside the same transaction.
     */
    @EntityGraph(attributePaths = {"seller", "category", "images", "serviceDetail"})
    Optional<Listing> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"seller", "category"})
    Page<Listing> findBySellerId(String sellerId, Pageable pageable);

    /**
     * One query for every combination of filters. It was previously three, chosen by
     * a chain of if-statements, and that chain dropped filters on the floor: asking
     * for a price range without a category returned the whole table, silently.
     *
     * Seller and category are fetched in the same query. Without the entity graph a
     * page of 20 listings issues 41 queries — one for the page and two per row —
     * which is the single worst performance trap in this domain.
     *
     * The joins to category and its parent are explicit LEFT joins so that category
     * filtering follows the tree one level down — browsing "Electronics" also returns
     * everything filed under "Laptops & Computers" — without an implicit inner join
     * on `parent` quietly excluding the listings filed directly under a top-level
     * category, which have no parent.
     *
     * `q` matches title or description, case-insensitively; the caller passes it
     * already lowercased and wrapped in wildcards.
     */
    @EntityGraph(attributePaths = {"seller", "category"})
    @Query("""
           select l from Listing l
             left join l.category c
             left join c.parent p
           where (:categoryId is null or c.id = :categoryId or p.id = :categoryId)
             and (:status is null or l.status = :status)
             and (:listingType is null or l.listingType = :listingType)
             and (:minPrice is null or l.price >= :minPrice)
             and (:maxPrice is null or l.price <= :maxPrice)
             and (:q is null or lower(l.title) like :q or lower(l.description) like :q)
           """)
    Page<Listing> search(@Param("categoryId") Integer categoryId,
                         @Param("status") ListingStatus status,
                         @Param("listingType") ListingType listingType,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("q") String q,
                         Pageable pageable);

    /**
     * A view is a counter bump, not a read-modify-write of the whole row: doing it in
     * the database avoids losing counts when two people open a listing at once.
     */
    @Modifying
    @Query("update Listing l set l.viewsCount = l.viewsCount + 1 where l.id = :id")
    void incrementViews(@Param("id") UUID id);
}
