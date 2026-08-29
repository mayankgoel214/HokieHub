package edu.vt.hokiehub.repository;

import edu.vt.hokiehub.domain.PriceCheckUnlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PriceCheckUnlockRepository extends JpaRepository<PriceCheckUnlock, Long> {
    boolean existsByListingIdAndUserId(UUID listingId, String userId);
}
