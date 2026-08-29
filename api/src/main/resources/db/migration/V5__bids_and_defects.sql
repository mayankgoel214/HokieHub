-- Two things a student marketplace needs that a "post it and hope" listing does
-- not give you: a way to offer less than the asking price, and a way to say what
-- is wrong with the thing before someone drives across campus to find out.

-- A bid is an offer on a listing.
--
-- One row per (listing, bidder): raising an offer updates the row rather than
-- stacking a second one, because "my bid" has to mean one thing when a seller is
-- comparing offers and when a bidder wants to withdraw.
CREATE TABLE bids (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    bidder_id  VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount     DECIMAL(10, 2) NOT NULL CHECK (amount > 0),
    message    VARCHAR(500),
    status     VARCHAR(20) NOT NULL DEFAULT 'active'
               CHECK (status IN ('active', 'withdrawn', 'accepted', 'declined')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT one_bid_per_bidder_per_listing UNIQUE (listing_id, bidder_id)
);

-- The seller reads this list constantly and always wants the strongest offer
-- first; nobody ever asks for their own bids in a hurry, but the withdraw path
-- looks them up by bidder.
CREATE INDEX idx_bids_listing_amount ON bids (listing_id, amount DESC);
CREATE INDEX idx_bids_bidder         ON bids (bidder_id);

-- What is wrong with the item, said out loud.
--
-- A separate table rather than a paragraph in the description, because severity
-- is the part a buyer actually filters on, and because a list renders as a list.
CREATE TABLE listing_defects (
    id            BIGSERIAL PRIMARY KEY,
    listing_id    UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    description   VARCHAR(200) NOT NULL,
    severity      VARCHAR(20) NOT NULL DEFAULT 'minor'
                  CHECK (severity IN ('minor', 'moderate', 'major')),
    display_order INTEGER NOT NULL DEFAULT 0
);

-- Defects are only ever fetched for a listing being displayed.
CREATE INDEX idx_listing_defects_listing_id ON listing_defects (listing_id);
