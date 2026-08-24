-- Baseline schema for the HokieHub marketplace.
--
-- Ported from the hand-run database/schema.sql so that schema changes are
-- versioned and applied automatically, rather than being a file someone has to
-- remember to run against the right database.

CREATE TABLE users (
    id                       VARCHAR(255) PRIMARY KEY,
    email                    VARCHAR(255) UNIQUE NOT NULL CHECK (email LIKE '%@vt.edu'),
    full_name                VARCHAR(255) NOT NULL,
    phone_number             VARCHAR(20),
    profile_image_url        VARCHAR(500),
    bio                      TEXT,
    preferred_contact_method VARCHAR(50) CHECK (preferred_contact_method IN ('email', 'phone', 'message')),
    created_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    id                 SERIAL PRIMARY KEY,
    name               VARCHAR(100) UNIQUE NOT NULL,
    description        TEXT,
    icon               VARCHAR(50),
    parent_category_id INTEGER REFERENCES categories(id) ON DELETE CASCADE,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE listings (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id    VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id  INTEGER NOT NULL REFERENCES categories(id),
    title        VARCHAR(255) NOT NULL,
    description  TEXT NOT NULL,
    price        DECIMAL(10, 2) NOT NULL CHECK (price >= 0),
    condition    VARCHAR(50) CHECK (condition IN ('new', 'like_new', 'good', 'fair', 'poor')),
    listing_type VARCHAR(50) NOT NULL CHECK (listing_type IN ('item', 'service')),
    status       VARCHAR(50) DEFAULT 'available' CHECK (status IN ('available', 'pending', 'sold', 'unavailable')),
    location     VARCHAR(255),
    views_count  INTEGER DEFAULT 0,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMP
);

CREATE TABLE service_details (
    id               SERIAL PRIMARY KEY,
    listing_id       UUID UNIQUE NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    subjects         TEXT[],
    availability     TEXT,
    hourly_rate      DECIMAL(10, 2),
    experience_level VARCHAR(50)
);

CREATE TABLE listing_images (
    id            SERIAL PRIMARY KEY,
    listing_id    UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    image_url     VARCHAR(500) NOT NULL,
    is_primary    BOOLEAN DEFAULT FALSE,
    display_order INTEGER DEFAULT 0,
    uploaded_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
