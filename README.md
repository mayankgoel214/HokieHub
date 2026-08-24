# HokieHub

A peer-to-peer marketplace for Virginia Tech students — textbooks, dorm
furniture, electronics, and services like tutoring — restricted to verified
`@vt.edu` accounts so buyers and sellers are actually on campus.

**Stack:** Next.js 15 · React 19 · TypeScript · **Java 21 · Spring Boot 3.5** ·
PostgreSQL · Flyway · Redis · Supabase Auth · Docker

## Architecture

Two services, split along a deliberate line:

```
  Next.js (web/)                Spring Boot (api/)             PostgreSQL
  ─────────────                 ──────────────────             ──────────
  pages, rendering,   ──JWT──>  domain model, ownership  ────>  schema owned
  Supabase session              rules, validation,              by Flyway
                                pagination, caching     ──────> Redis (category tree)
```

Supabase remains the identity provider: the browser already holds a Supabase
session, and the API validates that JWT rather than running a second account
system.

The domain moved out of the Next.js API routes and into a Spring service layer,
which is where a marketplace's rules actually belong — one ownership check that
every mutation passes through, validation at the request boundary, and a schema
under version control instead of a `schema.sql` someone has to remember to run.

See [api/README.md](api/README.md) for the design notes, including the N+1 fix,
the pagination cap, and why only the category tree is cached.

## Run it

The backend and its dependencies:

```bash
docker compose up --build
```

| | |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |

The web client:

```bash
npm install && npm run dev     # http://localhost:3000
```

## Tests

```bash
cd api
mvn test      # unit tests — fast, no Docker
mvn verify    # adds integration tests against a real PostgreSQL container
```

## Data model

Five tables. Categories are a two-level tree modelled as a self-reference —
twelve top-level categories, each with its own children — so filtering by
"Electronics" also returns everything under "Laptops & Computers".

```
users ──< listings >── categories ──┐
             │                      │ (parent_category_id)
             ├──< listing_images    └──────────┘
             └──1  service_details        (self-reference)
```

`service_details` exists because a tutoring listing needs subjects, availability
and an hourly rate that a used monitor does not — separated rather than left as
nullable columns on every row.
