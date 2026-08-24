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

## Design notes

**Why a separate service.** The Next.js API routes ran raw SQL against a
connection pool, with authorisation checked inline per route. Moving the domain
into a service layer puts every mutation behind one ownership check and makes
the rules testable without HTTP.

**The N+1 problem.** A listing needs its seller and category. Left to default
lazy loading, a page of 20 listings issues 41 queries — one for the page and two
per row. `ListingRepository` uses `@EntityGraph` so those come back in a single
join, and `open-in-view` is disabled so a lazy association can never be resolved
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
| `GET` | `/api/listings` | public — paged, filter by category, status, type, price |
| `GET` | `/api/listings/{id}` | public — includes images and service details |
| `GET` | `/api/listings/mine` | required |
| `POST` | `/api/listings` | required |
| `PUT` | `/api/listings/{id}` | required — owner only |
| `DELETE` | `/api/listings/{id}` | required — owner only |
| `GET` | `/api/categories` | public — the full tree, cached |

Errors are RFC 7807 `application/problem+json`. Validation failures carry an
`errors` object keyed by field name.
