package edu.vt.hokiehub.repository;

import edu.vt.hokiehub.domain.ListingImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ListingImageRepository extends JpaRepository<ListingImage, Integer> {

    /**
     * The one image a browse card needs, for a whole page of listings at once.
     *
     * Adding images to the page query's entity graph would have been the obvious
     * move and the wrong one: a collection join multiplies rows, and Hibernate
     * responds by paginating in memory — reading the whole table to show twenty.
     * One extra query for the page costs one round trip and no correctness.
     */
    @Query("""
           select i.listing.id as listingId, i.imageUrl as imageUrl, i.id as imageId
           from ListingImage i
           where i.listing.id in :listingIds and i.primary = true
           """)
    List<PrimaryImage> findPrimaryFor(@Param("listingIds") Collection<UUID> listingIds);

    long countByListingId(UUID listingId);

    interface PrimaryImage {
        UUID getListingId();
        String getImageUrl();
        Integer getImageId();

        /**
         * Where a browser should ask for it: the external link when there is one,
         * this service's own endpoint when the bytes are stored here.
         */
        default String url() {
            return getImageUrl() != null ? getImageUrl() : "/api/images/" + getImageId();
        }
    }
}
