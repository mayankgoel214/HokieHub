-- Keyword search matches `lower(title) LIKE '%term%'`. A leading wildcard makes a
-- B-tree index useless, so this uses pg_trgm, which indexes character trigrams and
-- does support an unanchored LIKE. Without it, every search is a sequential scan
-- over the whole listings table.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_listings_title_trgm       ON listings USING gin (lower(title) gin_trgm_ops);
CREATE INDEX idx_listings_description_trgm ON listings USING gin (lower(description) gin_trgm_ops);
