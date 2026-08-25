# HokieHub API

The marketplace service behind HokieHub: listings for items and services, a
two-level category tree, and ownership rules — as a Spring Boot application over
PostgreSQL.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Data JPA / Hibernate · PostgreSQL ·
Flyway · Redis · Spring Security (OAuth2 resource server) · springdoc OpenAPI ·
JUnit 5 · Mockito · Testcontainers · Docker

## Run it

```bash
docker compose up --build          # from the repository root
```

| | |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

Flyway applies the schema, the seeded category tree and the indexes on first
boot, so there is no manual database setup.

## Tests

```bash
mvn test      # unit tests only — no Docker needed
mvn verify    # adds integration tests against a real PostgreSQL container
```

Unit tests (`*Test`) mock the repositories and cover the business rules.
Integration tests (`*IT`) start PostgreSQL 16 through Testcontainers and run the
real migrations, so the schema, the `CHECK` constraints, the `text[]` column and
the Hibernate mapping are all verified together — none of which an in-memory
database would catch.

## Deploying

Vercel cannot host a JVM application, so the API deploys separately from the web
client. On Railway: **New Project → Deploy from GitHub repo**, set the service's
root directory to `api`, then add a PostgreSQL and a Redis plugin to the same
project and set these variables on the API service.

| Variable | Value |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DATABASE_USER` | `${{Postgres.PGUSER}}` |
| `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `REDIS_HOST` | `${{Redis.REDISHOST}}` |
| `REDIS_PORT` | `${{Redis.REDISPORT}}` |
| `SUPABASE_JWT_SECRET` | the Supabase project's JWT secret |
| `CORS_ALLOWED_ORIGINS` | the deployed web client's origin |

`DATABASE_URL` is spelled out rather than referencing Railway's own
`DATABASE_URL`, because Railway supplies a `postgresql://user:pass@host/db` URI
and the JDBC driver needs a `jdbc:postgresql://` URL with the credentials passed
separately. Railway sets `PORT` itself, which `application.yml` already reads.

Flyway runs on first boot, so the schema and the seeded categories are applied
without a manual step. The web client then needs `NEXT_PUBLIC_API_URL` pointed
at the deployed API.

## Design notes

**Why a separate service.** The Next.js API routes ran raw SQL against a
connection pool, with authorisation checked inline per route. Moving the domain
into a service layer puts every mutation behind one ownership check and makes
the rules testable without HTTP.

**The N+1 problem.** A listing needs its seller and category. Left to the
mapping's own fetch plan, rendering a page of 20 listings costs **62 statements**;
through the entity graph it costs **2**. Both figures are measured, not reasoned:
`ListingQueryCountIT` reads Hibernate's own statement counter for each.

The 62 breaks down as the page query, the count query pagination issues, and
three loads per row — seller, category, and service detail. That third one is
the interesting one. `Listing.serviceDetail` is annotated `FetchType.LAZY` and it
is ignored, because it is the inverse side of a one-to-one: Hibernate cannot hand
out a proxy without first asking the database whether the row exists. No
annotation on the entity switches that off. What does switch it off is the fetch
graph — a Spring Data `@EntityGraph` treats every attribute it does not name as
lazy — which is why the same page comes back in one join.

`open-in-view` is disabled as well, so a lazy association can never be resolved
accidentally during JSON serialisation.

**Pagination.** The original `getAllListings()` was `SELECT l.* FROM listings`
with no limit, which is fine at ten rows and not at ten thousand. Browse is now
paged, and the page size is capped server-side so a client cannot ask for
everything.

**Indexes.** The original schema defined none. Postgres does not index foreign
keys automatically, and both `seller_id` and `category_id` are joined on every
listing page — `V3__indexes.sql` adds those plus the ordering and status indexes
the browse query needs.

**Category filtering follows the tree.** Filtering by a top-level category also
returns listings filed under its subcategories, so browsing "Electronics" does
not miss everything under "Laptops & Computers".

**Caching.** Only the category tree is cached. It is read on nearly every page
and changes almost never, which is exactly the shape that benefits; caching
listings would mean invalidating on every write for very little gain.

**Authentication.** Supabase remains the identity provider — the browser already
holds a Supabase session, so this service validates that JWT rather than running
a second account system. Browsing is public; anything that writes requires a
valid token, and ownership is enforced in the service layer.

## Layout

```
src/main/java/edu/vt/hokiehub/
  domain/      JPA entities and the enum converters that match the CHECK constraints
  repository/  Spring Data repositories, entity graphs, the view-counter update
  service/     business rules and ownership checks
  web/         controllers, DTOs, and one exception handler for all error responses
  config/      security, cache, OpenAPI
src/main/resources/db/migration/
  V1__initial_schema.sql
  V2__seed_categories.sql
  V3__indexes.sql
```

## Endpoints

| Method | Path | Auth |
|---|---|---|
| `GET` | `/api/listings` | public — paged; `q` searches title and description, and filters by category, status, type, price |
| `GET` | `/api/listings/{id}` | public — includes images and service details |
| `GET` | `/api/listings/mine` | required |
| `POST` | `/api/listings` | required |
| `PUT` | `/api/listings/{id}` | required — owner only |
| `DELETE` | `/api/listings/{id}` | required — owner only |
| `GET` | `/api/categories` | public — the full tree, cached |

Errors are RFC 7807 `application/problem+json`. Validation failures carry an
`errors` object keyed by field name.
