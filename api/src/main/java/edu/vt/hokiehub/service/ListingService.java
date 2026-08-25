package edu.vt.hokiehub.service;

import edu.vt.hokiehub.domain.*;
import edu.vt.hokiehub.exception.ForbiddenException;
import edu.vt.hokiehub.exception.NotFoundException;
import edu.vt.hokiehub.repository.CategoryRepository;
import edu.vt.hokiehub.repository.ListingRepository;
import edu.vt.hokiehub.repository.UserRepository;
import edu.vt.hokiehub.web.dto.CreateListingRequest;
import edu.vt.hokiehub.web.dto.UpdateListingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ListingService {

    private final ListingRepository listings;
    private final CategoryRepository categories;
    private final UserRepository users;

    public ListingService(ListingRepository listings, CategoryRepository categories, UserRepository users) {
        this.listings = listings;
        this.categories = categories;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Page<Listing> search(Integer categoryId, String status, String listingType,
                                BigDecimal minPrice, BigDecimal maxPrice, String q,
                                Pageable pageable) {
        return listings.search(
                categoryId,
                status == null ? null : ListingStatus.from(status),
                listingType == null ? null : ListingType.from(listingType),
                minPrice,
                maxPrice,
                likePattern(q),
                pageable);
    }

    /**
     * A blank `q` means "no keyword", not "match the empty string", so it collapses
     * to null and the predicate drops out of the query entirely.
     */
    private static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return "%" + q.trim().toLowerCase() + "%";
    }

    @Transactional(readOnly = true)
    public Page<Listing> findBySeller(String sellerId, Pageable pageable) {
        return listings.findBySellerId(sellerId, pageable);
    }

    /**
     * Reading a listing also bumps its view counter, which is why this is not a
     * read-only transaction.
     */
    @Transactional
    public Listing findById(UUID id, boolean countView) {
        Listing listing = listings.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Listing " + id + " not found"));
        if (countView) {
            listings.incrementViews(id);
        }
        return listing;
    }

    @Transactional
    public Listing create(Caller caller, CreateListingRequest request) {
        User seller = users.findById(caller.id()).orElseGet(() -> provision(caller));
        Category category = categories.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Category " + request.categoryId() + " not found"));

        ListingType type = ListingType.from(request.listingType());

        Listing listing = new Listing(seller, category, request.title(),
                request.description(), request.price(), type);
        if (request.condition() != null) {
            listing.setCondition(ItemCondition.from(request.condition()));
        }
        listing.setLocation(request.location());
        listing.setExpiresAt(request.expiresAt());

        if (type == ListingType.SERVICE && request.serviceDetails() != null) {
            var sd = request.serviceDetails();
            listing.setServiceDetail(new ServiceDetail(
                    sd.subjects(), sd.availability(), sd.hourlyRate(), sd.experienceLevel()));
        }

        List<CreateListingRequest.ImagePayload> images =
                request.images() == null ? List.of() : request.images();
        for (int i = 0; i < images.size(); i++) {
            var img = images.get(i);
            boolean isPrimary = img.isPrimary() != null ? img.isPrimary() : i == 0;
            int order = img.displayOrder() != null ? img.displayOrder() : i;
            listing.addImage(new ListingImage(img.imageUrl(), isPrimary, order));
        }

        // Cascade persists the service detail and every image in one flush, rather
        // than the original implementation's insert-per-image loop.
        return listings.save(listing);
    }

    @Transactional
    public Listing update(UUID id, String userId, UpdateListingRequest request) {
        Listing listing = listings.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Listing " + id + " not found"));
        requireOwner(listing, userId);

        if (request.categoryId() != null) {
            listing.setCategory(categories.findById(request.categoryId())
                    .orElseThrow(() -> new NotFoundException("Category " + request.categoryId() + " not found")));
        }
        if (request.title() != null) listing.setTitle(request.title());
        if (request.description() != null) listing.setDescription(request.description());
        if (request.price() != null) listing.setPrice(request.price());
        if (request.condition() != null) listing.setCondition(ItemCondition.from(request.condition()));
        if (request.status() != null) listing.setStatus(ListingStatus.from(request.status()));
        if (request.location() != null) listing.setLocation(request.location());
        if (request.expiresAt() != null) listing.setExpiresAt(request.expiresAt());

        return listing;
    }

    @Transactional
    public void delete(UUID id, String userId) {
        Listing listing = listings.findById(id)
                .orElseThrow(() -> new NotFoundException("Listing " + id + " not found"));
        requireOwner(listing, userId);
        listings.delete(listing);
    }

    /**
     * A valid Supabase token can arrive from someone this service has never stored,
     * so the account is created on first write rather than rejected.
     *
     * The database also constrains the address with CHECK (email LIKE '%@vt.edu'),
     * but relying on that alone surfaces a constraint violation as a 500. Checking
     * here turns "you are not a Virginia Tech account" into an answer the caller
     * can act on.
     */
    private User provision(Caller caller) {
        String email = caller.email() == null ? null : caller.email().trim().toLowerCase();
        if (email == null || !email.endsWith("@vt.edu")) {
            throw new ForbiddenException("HokieHub is limited to @vt.edu accounts");
        }
        return users.save(new User(caller.id(), email, caller.fullName()));
    }

    /**
     * Ownership is checked in the service rather than the controller, so every path
     * that mutates a listing goes through the same check.
     */
    private void requireOwner(Listing listing, String userId) {
        if (!listing.isOwnedBy(userId)) {
            throw new ForbiddenException("You do not own listing " + listing.getId());
        }
    }
}
