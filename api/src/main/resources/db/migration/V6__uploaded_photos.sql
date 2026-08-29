-- Real photographs of the actual item, not a generated cover.
--
-- The bytes live in Postgres and are served by this service. An object store
-- would be the answer at scale, but it would also mean another account, another
-- credential and another thing that can be misconfigured into a broken image —
-- and the whole catalogue is a few megabytes. Uploads are capped so that stays
-- true.
ALTER TABLE listing_images
    ADD COLUMN content_type VARCHAR(60),
    ADD COLUMN size_bytes   INTEGER,
    ADD COLUMN data         BYTEA;

-- image_url held a link to somewhere else. An uploaded photo has no such link —
-- it is served from /api/images/{id} — so the column can no longer be required.
ALTER TABLE listing_images ALTER COLUMN image_url DROP NOT NULL;

-- Exactly one of the two must be present: a row is either a link to an external
-- image or bytes stored here, never neither.
ALTER TABLE listing_images
    ADD CONSTRAINT listing_image_has_content
    CHECK (image_url IS NOT NULL OR data IS NOT NULL);

-- Two megabytes a photo, enforced by the database as well as the API, because a
-- limit that only exists in application code is a limit until someone writes a
-- second code path.
ALTER TABLE listing_images
    ADD CONSTRAINT listing_image_size_sane
    CHECK (size_bytes IS NULL OR size_bytes <= 2097152);
