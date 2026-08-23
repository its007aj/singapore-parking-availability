# Singapore Parking Finder

An API-only service that helps users find nearby available car parks in Singapore, using
HDB's static car park dataset and data.gov.sg's live car park availability feed.

## Running it

You only need Docker and Docker Compose — no local Java, Maven, or Postgres required.

```bash
docker compose up --build -d
```

This starts three containers:

- `postgres` — Postgres 16, with a healthcheck gating startup order. Its port is
  published to the host (`5432:5432`) for connecting a DB client (e.g. pgAdmin) directly
  — see [Database access](#database-access) below. **This means the stack won't start if
  you already have a local Postgres bound to port 5432** — stop that instance first, or
  edit the port mapping in `docker-compose.yml`.
- `app` — the Spring Boot API, built via a multi-stage Dockerfile (JDK 21 to build, a
  slim JRE 21 Alpine image to run). It waits for Postgres to report healthy, then on
  first boot loads the static car park dataset and starts polling live availability.
- `test` — runs the full test suite (`./mvnw test`, including the tests that need a live
  database) against the already-running Postgres, then exits. This runs on **every**
  `docker compose up`, not just on request — expect it to add real time (dependency
  resolution + the full suite) to what would otherwise be a quick startup, and to see it
  listed as an exited container in `docker compose ps` once it finishes. Check its result
  with `docker compose logs test`.

Once running, the API is at `http://localhost:8080`. A first request may return few or
no results for a minute or two until the first data loads — check readiness with:

```bash
curl http://localhost:8080/actuator/health
```

Logs (`docker compose logs -f app`) show the load/sync lifecycle:

```
Loaded 2270 car parks into the database
Synced live availability for 2422 car park/lot-type combinations
```

To stop: `docker compose down` (add `-v` to also drop the Postgres data volume).

## Commands reference

Every command actually used while building and operating this project, grouped by task.

### Docker Compose (primary way to run this)

| Command | What it does |
|---|---|
| `docker compose up --build -d` | Build images (if changed) and start both containers, detached |
| `docker compose up --build` | Same, but attached — streams logs, blocks the terminal (Ctrl+C stops it) |
| `docker compose ps` | Show container status |
| `docker compose logs -f app` | Tail the app's logs |
| `docker compose logs -f postgres` | Tail Postgres's logs |
| `docker compose stop app` | Stop just the app container (e.g. to run the app locally instead, against the same Postgres) |
| `docker compose up -d app postgres` | Start only the app and Postgres, skipping the `test` service's run |
| `docker compose logs test` | See the test suite's output/result from the last `up` |
| `docker compose up -d app` | Start/restart just the app container |
| `docker compose build` | Rebuild the app image without starting anything |
| `docker compose build --build-arg SKIP_TESTS=false` | Rebuild the **app** image with its DB-independent tests actually run — the build fails if any fail. `SingaporeApplicationTests` is excluded even here, since it needs Postgres and `docker build` has no database reachable; that one only runs via the `test` service below, against the real, networked Postgres |
| `docker compose down` | Stop and remove all containers; **keeps** the Postgres data volume |
| `docker compose down -v` | Stop and remove all containers **and** the Postgres data volume — use this if you hit a schema mismatch error after changing `schema.sql`, since `CREATE TABLE IF NOT EXISTS` never alters an existing table |

### Database access

Postgres's port is published, so you can connect any DB client directly:

- **Host/port:** `localhost` / `5432`
- **Database / user / password:** all `parking`

In pgAdmin: *Register → Server*, any name, then on the *Connection* tab enter the values
above. Via `psql` without installing anything locally:

```bash
docker exec -it singapore-postgres-1 psql -U parking -d parking
```

### Local development (optional — requires JDK 21)

Docker is the only requirement for *running* this project, but for local iteration
(fast test feedback, IDE debugging) you'll want JDK 21 and to point the app at
Postgres running in Docker:

```bash
# Start only Postgres, keep the app itself local
docker compose up -d postgres

# Run tests that don't need a database
./mvnw -q -Dtest='!SingaporeApplicationTests' test

# Run the full test suite (SingaporeApplicationTests needs Postgres — the command above already started it)
./mvnw test

# Run the app locally, pointed at Docker's Postgres
DB_HOST=localhost DB_PORT=5432 DB_NAME=parking DB_USER=parking DB_PASSWORD=parking \
  ./mvnw spring-boot:run
```

If you'd rather not touch the project's own Postgres data, use a disposable one instead
(clean up the container when you're done):

```bash
docker run -d --name parking-test-pg -e POSTGRES_DB=parking -e POSTGRES_USER=parking \
  -e POSTGRES_PASSWORD=parking -p 55432:5432 postgres:16-alpine

DB_HOST=localhost DB_PORT=55432 DB_NAME=parking DB_USER=parking DB_PASSWORD=parking \
  ./mvnw spring-boot:run

docker rm -f parking-test-pg
```

### Debugging

**From an IDE (recommended):** run the `SingaporeApplication` configuration in Debug
mode, with `DB_HOST=localhost`, `DB_PORT=5432` (or `55432` for a disposable Postgres),
`DB_NAME`/`DB_USER`/`DB_PASSWORD=parking` set as environment variables on that
configuration. Most IDEs (IntelliJ included) attach a debugger automatically — no extra
flags needed.

**From the command line**, start the app with a JDWP agent and attach separately:

```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

Then in your IDE: *Run → Edit Configurations → Remote JVM Debug*, host `localhost`, port
`5005`, and click Debug to attach. Use `suspend=y` instead of `suspend=n` if you need to
debug the startup sequence itself (the JVM then waits for a debugger before doing anything).

**Port already in use?** Only one process can bind 8080 (the app) or 5432 (Postgres) at
a time — if you're running the app locally, stop the Docker Compose `app` container
first (`docker compose stop app`); if a stray local process is holding a port, find and
stop it (`lsof -nP -iTCP:8080 -sTCP:LISTEN`, then `kill <pid>`).

### Testing the API

```bash
curl "http://localhost:8080/api/v1/carparks/nearby?lat=1.3010&lon=103.8541"
curl "http://localhost:8080/api/v1/carparks/nearby?lat=1.3010&lon=103.8541&radiusKm=1&limit=5"
curl http://localhost:8080/actuator/health
```

Or open the first URL directly in a browser.

## API

### `GET /api/v1/carparks/nearby`

Returns car parks with at least one available lot, sorted by distance from the given
location, nearest first.

| Parameter  | Required | Default | Constraints        | Description                                  |
|------------|----------|---------|---------------------|-----------------------------------------------|
| `lat`      | yes      | —       | -90.0 to 90.0       | User latitude (WGS84 decimal degrees)         |
| `lon`      | yes      | —       | -180.0 to 180.0     | User longitude (WGS84 decimal degrees)        |
| `radiusKm` | no       | `3.0`   | 0.1 to 50.0         | Search radius in kilometres                   |
| `limit`    | no       | `20`    | 1 to 100            | Maximum number of results                     |

Only lots for regular cars (`lot_type = C`) are considered — see DESIGN.md for why.

**Example request:**

```
GET /api/v1/carparks/nearby?lat=1.3010&lon=103.8541&radiusKm=1&limit=5
```

**Example response (`200 OK`):**

```json
[
  {
    "carParkNo": "ACB",
    "address": "BLK 270/271 ALBERT CENTRE BASEMENT CAR PARK",
    "latitude": 1.3010626054202958,
    "longitude": 103.85411771659147,
    "distanceKm": 0.0072,
    "lotsAvailable": 2,
    "totalLots": 91,
    "lastUpdated": "2026-08-23T06:36:53Z",
    "stale": false
  }
]
```

- `lastUpdated` is when the *source* last reported this car park's lot count (not when
  we last polled) — see the staleness discussion in DESIGN.md.
- `stale: true` means that timestamp is older than the configured freshness window
  (`parking.availability.stale-after-minutes`, default 15 minutes). Stale results are
  still returned rather than hidden — see DESIGN.md for the reasoning.
- No matches within the radius returns `200 OK` with `[]`, not an error.

**Validation error (`400 Bad Request`):**

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "must be less than or equal to 90.0",
  "instance": "/api/v1/carparks/nearby"
}
```

### `GET /actuator/health`, `GET /actuator/info`

Standard Spring Boot Actuator endpoints, exposed for readiness/liveness checks
(`components.db.status` reflects the Postgres connection).

## Architecture

```
┌─────────────────────┐        ┌──────────────────────────┐
│ data.gov.sg          │        │ data.gov.sg               │
│ HDB carpark dataset   │        │ live availability API     │
│ (poll-download + S3)  │        │                            │
└──────────┬───────────┘        └────────────┬───────────────┘
           │ on startup                       │ every poll-interval-ms
           ▼                                  ▼
 ┌────────────────────┐            ┌───────────────────────────┐
 │ CarParkStaticData-  │            │ CarParkAvailabilityClient  │
 │ Client + Loader     │            │ + SyncService + Scheduler  │
 │ (falls back to      │            │ (falls back to keeping     │
 │ bundled CSV)        │            │ last-known-good data)      │
 └──────────┬──────────┘            └────────────┬───────────────┘
            │ upsert                              │ upsert
            ▼                                      ▼
      ┌─────────────────────────────────────────────────┐
      │                    PostgreSQL                     │
      │   car_park            car_park_availability        │
      └──────────────────────────┬──────────────────────┘
                                  │ join, nearest-first
                                  ▼
                     ┌─────────────────────────┐
                     │ NearbyCarParkService/     │
                     │ Query/Controller           │
                     │  GET /api/v1/carparks/     │
                     │  nearby                    │
                     └─────────────────────────┘
```

**Package layout** (`com.carpark.singapore.*`):

- `geo` — coordinate transform (SVY21 → WGS84) and haversine distance, pure/stateless.
- `carpark` — static dataset: CSV parsing, live+fallback client, DB loader.
- `availability` — live availability: DTOs, client, mapper, sync service, scheduler.
- `search` — the read side: joins car park + availability, computes proximity/staleness.
- `web` — cross-cutting API concerns (validation error formatting).
- `config` — infrastructure beans (`RestClient.Builder`, `Clock`).

**Storage:** PostgreSQL via Spring Data JPA — `CarParkEntity`/`CarParkAvailabilityEntity`
map to the two tables (`schema.sql` is the source of truth; Hibernate only validates
against it, never generates DDL), and Spring Data JPA repositories handle both the bulk
upserts (`saveAll`) and the nearby-search join (a JPQL query with an explicit `JOIN ... ON`,
since the two entities have no relationship — see DESIGN.md for why). See DESIGN.md for
the storage trade-offs in more detail.

**Resilience:** each external HTTP call (static dataset fetch, live availability fetch)
is wrapped in a resilience4j `@Retry` + `@CircuitBreaker`, configured in
`application.yml`. See DESIGN.md for the full reasoning on degrade/reconcile behaviour.

**Coordinate transform:** the HDB dataset publishes locations in SVY21 (EPSG:3414,
Singapore's projected coordinate system, metres from a local false origin) — not
WGS84 lat/lon. `Svy21ToWgs84Converter` implements the inverse transverse-Mercator
formula to convert every row at load time.
