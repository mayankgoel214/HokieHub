-- The original schema shipped with no indexes at all. Every one of these backs a
-- query the application actually issues; without them each of those is a
-- sequential scan over the whole listings table.

-- Browse and "my listings" both order by recency.
CREATE INDEX idx_listings_created_at ON listings (created_at DESC);

-- Foreign keys are not indexed automatically in Postgres, and both are joined on
-- every listing page.
CREATE INDEX idx_listings_seller_id   ON listings (seller_id);
CREATE INDEX idx_listings_category_id ON listings (category_id);

-- The default browse view filters to available listings, newest first.
CREATE INDEX idx_listings_status_created_at ON listings (status, created_at DESC);

-- Category filtering walks one level down the tree.
CREATE INDEX idx_categories_parent_id ON categories (parent_category_id);

-- Child rows are always fetched by their parent listing.
CREATE INDEX idx_listing_images_listing_id ON listing_images (listing_id);

-- Case-insensitive lookup by email, used when reconciling a Supabase account.
CREATE INDEX idx_users_email_lower ON users (lower(email));
