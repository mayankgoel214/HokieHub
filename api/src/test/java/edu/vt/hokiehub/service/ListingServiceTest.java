package edu.vt.hokiehub.service;

import edu.vt.hokiehub.domain.*;
import edu.vt.hokiehub.exception.ForbiddenException;
import edu.vt.hokiehub.exception.NotFoundException;
import edu.vt.hokiehub.repository.CategoryRepository;
import edu.vt.hokiehub.repository.ListingRepository;
import edu.vt.hokiehub.repository.UserRepository;
import edu.vt.hokiehub.web.dto.CreateListingRequest;
import edu.vt.hokiehub.web.dto.UpdateListingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Business rules only — no Spring context and no database, so these run in
 * milliseconds and fail for exactly one reason.
 */
@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    private static final String OWNER = "owner-user-id";
    private static final String STRANGER = "some-other-user-id";

    @Mock private ListingRepository listings;
    @Mock private CategoryRepository categories;
    @Mock private UserRepository users;
    @InjectMocks private ListingService service;

    private User owner;
    private Category category;

    @BeforeEach
    void setUp() {
        owner = new User(OWNER, "owner@vt.edu", "Owner Student");
        category = mock(Category.class);
    }

    private Listing listingOwnedByOwner() {
        return new Listing(owner, category, "Calculus textbook", "Barely used",
                new BigDecimal("40.00"), ListingType.ITEM);
    }

    @Test
    @DisplayName("creating a listing attaches the seller, category and images")
    void createAttachesRelations() {
        when(users.findById(OWNER)).thenReturn(Optional.of(owner));
        when(categories.findById(3)).thenReturn(Optional.of(category));
        when(listings.save(any(Listing.class))).thenAnswer(i -> i.getArgument(0));

        var request = new CreateListingRequest(
                3, "Desk lamp", "Works fine", new BigDecimal("12.50"),
                "good", "item", "Pritchard Hall", null, null,
                List.of(new CreateListingRequest.ImagePayload("https://img/1.jpg", null, null),
                        new CreateListingRequest.ImagePayload("https://img/2.jpg", null, null)));

        Listing created = service.create(OWNER, request);

        assertThat(created.getSeller()).isEqualTo(owner);
        assertThat(created.getTitle()).isEqualTo("Desk lamp");
        assertThat(created.getCondition()).isEqualTo(ItemCondition.GOOD);
        assertThat(created.getStatus()).isEqualTo(ListingStatus.AVAILABLE);
        assertThat(created.getImages()).hasSize(2);
        // First image defaults to primary when the client does not say which is.
        assertThat(created.getImages().get(0).getPrimary()).isTrue();
        assertThat(created.getImages().get(1).getPrimary()).isFalse();
    }

    @Test
    @DisplayName("a service listing carries its service details")
    void createServiceListing() {
        when(users.findById(OWNER)).thenReturn(Optional.of(owner));
        when(categories.findById(5)).thenReturn(Optional.of(category));
        when(listings.save(any(Listing.class))).thenAnswer(i -> i.getArgument(0));

        var request = new CreateListingRequest(
                5, "Calculus tutoring", "MATH 1225 and 1226", new BigDecimal("25.00"),
                null, "service", null, null,
                new CreateListingRequest.ServiceDetailPayload(
                        List.of("MATH 1225", "MATH 1226"), "Evenings",
                        new BigDecimal("25.00"), "3 semesters"),
                null);

        Listing created = service.create(OWNER, request);

        assertThat(created.getListingType()).isEqualTo(ListingType.SERVICE);
        assertThat(created.getServiceDetail()).isNotNull();
        assertThat(created.getServiceDetail().getSubjects()).containsExactly("MATH 1225", "MATH 1226");
    }

    @Test
    @DisplayName("creating against a category that does not exist is a 404, not a constraint violation")
    void createWithUnknownCategory() {
        when(users.findById(OWNER)).thenReturn(Optional.of(owner));
        when(categories.findById(999)).thenReturn(Optional.empty());

        var request = new CreateListingRequest(999, "Thing", "Desc",
                BigDecimal.ONE, null, "item", null, null, null, null);

        assertThatThrownBy(() -> service.create(OWNER, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category 999");
    }

    @Test
    @DisplayName("a stranger cannot update someone else's listing")
    void updateRejectsNonOwner() {
        UUID id = UUID.randomUUID();
        when(listings.findWithDetailsById(id)).thenReturn(Optional.of(listingOwnedByOwner()));

        var request = new UpdateListingRequest(null, "Hijacked title", null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(id, STRANGER, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("the owner can update, and absent fields are left alone")
    void updateAppliesOnlyProvidedFields() {
        UUID id = UUID.randomUUID();
        Listing existing = listingOwnedByOwner();
        when(listings.findWithDetailsById(id)).thenReturn(Optional.of(existing));

        var request = new UpdateListingRequest(null, null, null,
                new BigDecimal("35.00"), null, "sold", null, null);

        Listing updated = service.update(id, OWNER, request);

        assertThat(updated.getPrice()).isEqualByComparingTo("35.00");
        assertThat(updated.getStatus()).isEqualTo(ListingStatus.SOLD);
        // Untouched because the request omitted it.
        assertThat(updated.getTitle()).isEqualTo("Calculus textbook");
    }

    @Test
    @DisplayName("a stranger cannot delete someone else's listing")
    void deleteRejectsNonOwner() {
        UUID id = UUID.randomUUID();
        when(listings.findById(id)).thenReturn(Optional.of(listingOwnedByOwner()));

        assertThatThrownBy(() -> service.delete(id, STRANGER))
                .isInstanceOf(ForbiddenException.class);
        verify(listings, never()).delete(any());
    }

    @Test
    @DisplayName("the owner can delete")
    void deleteAllowsOwner() {
        UUID id = UUID.randomUUID();
        Listing existing = listingOwnedByOwner();
        when(listings.findById(id)).thenReturn(Optional.of(existing));

        service.delete(id, OWNER);

        verify(listings).delete(existing);
    }

    @Test
    @DisplayName("fetching a missing listing is a 404")
    void findByIdMissing() {
        UUID id = UUID.randomUUID();
        when(listings.findWithDetailsById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("reading a listing bumps its view counter in the database")
    void findByIdCountsView() {
        UUID id = UUID.randomUUID();
        when(listings.findWithDetailsById(id)).thenReturn(Optional.of(listingOwnedByOwner()));

        service.findById(id, true);

        verify(listings).incrementViews(id);
    }
}
