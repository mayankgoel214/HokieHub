package edu.vt.hokiehub.repository;

import edu.vt.hokiehub.domain.PriceCheck;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PriceCheckRepository extends JpaRepository<PriceCheck, UUID> {

    /** Sources come back with it; the estimate is not shown without them. */
    @EntityGraph(attributePaths = {"sources"})
    Optional<PriceCheck> findByListingId(UUID listingId);
}
