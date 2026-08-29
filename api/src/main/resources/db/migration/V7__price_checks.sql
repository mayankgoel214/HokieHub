-- What is this thing actually worth second hand?
--
-- The seller names whatever price they like. A buyer can ask for an independent
-- read: what the item is, what comparable ones have sold for, and how this
-- asking price sits against that.

-- One analysis per listing, not one per buyer.
--
-- The answer does not depend on who asked, and the analysis costs a paid model
-- call — so it is computed once and everyone who unlocks it reads the same row.
-- Unique on listing_id enforces that rather than trusting the service to check.
CREATE TABLE price_checks (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id     UUID NOT NULL UNIQUE REFERENCES listings(id) ON DELETE CASCADE,

    -- ready:            an estimate backed by comparables
    -- no_comparables:   the search found nothing usable, and there is no estimate
    -- failed:           the model or the search errored
    --
    -- no_comparables is a first-class outcome and not an error. An estimate with
    -- nothing behind it would be the model guessing, presented as a market price,
    -- which is worse than saying nothing.
    status         VARCHAR(20) NOT NULL
                   CHECK (status IN ('ready', 'no_comparables', 'failed')),

    identified_item   VARCHAR(300),
    estimated_low     DECIMAL(10, 2),
    estimated_typical DECIMAL(10, 2),
    estimated_high    DECIMAL(10, 2),

    -- How the seller's asking price sits against the estimate.
    verdict        VARCHAR(20)
                   CHECK (verdict IN ('below_market', 'fair', 'above_market')),

    summary        TEXT,
    failure_reason VARCHAR(400),

    -- Which model produced it, so an old answer can be told apart from a new one
    -- after the model changes.
    model          VARCHAR(80),
    grounded       BOOLEAN NOT NULL DEFAULT FALSE,

    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- An estimate is only allowed to exist when the analysis actually succeeded.
    CONSTRAINT estimate_only_when_ready
        CHECK (status <> 'ready' OR estimated_typical IS NOT NULL),

    CONSTRAINT estimate_range_ordered
        CHECK (estimated_low IS NULL OR estimated_high IS NULL
               OR estimated_low <= estimated_high)
);

-- The evidence. Without these the estimate is an assertion.
--
-- Displayed to the buyer alongside the number, so they can judge the comparison
-- themselves rather than take the figure on faith.
CREATE TABLE price_check_sources (
    id             BIGSERIAL PRIMARY KEY,
    price_check_id UUID NOT NULL REFERENCES price_checks(id) ON DELETE CASCADE,
    title          VARCHAR(400) NOT NULL,
    url            VARCHAR(1000),
    price          DECIMAL(10, 2),
    note           VARCHAR(300),
    display_order  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_price_check_sources_check ON price_check_sources (price_check_id);

-- Who has paid to see it.
--
-- No payment processor is wired up: amount_cents records what it would have
-- cost, and unlocking is free in this build. The row exists so that the gate is
-- enforced server-side rather than by hiding a section in the browser.
CREATE TABLE price_check_unlocks (
    id           BIGSERIAL PRIMARY KEY,
    listing_id   UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    user_id      VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount_cents INTEGER NOT NULL DEFAULT 199,
    unlocked_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT one_unlock_per_user_per_listing UNIQUE (listing_id, user_id)
);

CREATE INDEX idx_price_check_unlocks_user ON price_check_unlocks (user_id);
