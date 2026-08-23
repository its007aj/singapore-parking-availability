# Decision Log

A chronological record of the choices made while building this project with Claude Code,
for reviewing what was decided and why. Not a deliverable — just a working note.

## Scoping the build

- Asked to "integrate the car park availability API." No static data foundation existed
  yet (bare Spring Boot scaffold only), so I was asked to choose a scope: full minimal
  pipeline (static + availability) / availability-only with a bare table / an in-memory
  stub. **Chose: full minimal pipeline** — a stub wouldn't prove anything end to end.
- No JDK 21 was installed locally (only JDK 8), which blocked local builds (Docker's own
  JDK would still work, just slower to iterate against). **Chose: install JDK 21 locally**
  for fast local test/compile feedback, verify the real deliverable via Docker separately.
- Instead of building everything in one pass, **asked for a step-by-step plan** and
  approved each step individually before moving to the next:
  1. Coordinate transform
  2. Static car park data pipeline
  3. Live availability integration
  4. Nearby search REST API
  5. Dockerization
  6. Docs (README/DESIGN/AI.md)

## Resilience: rejected, then re-added

- First pass added `resilience4j` (retry + circuit breaker) unprompted, matching config
  already present in `application.yml`. **Rejected**: *"I do not want resilience, only
  minimal things that are needed to complete the project."* Static-data client was
  rebuilt as a single-attempt fetch + timeout + fallback, no retry library.
- Later, **reversed that decision** and asked to add resilience4j back after all — the
  config in `application.yml` existed for a reason and a flaky government API genuinely
  benefits from retry/circuit-breaker semantics. Re-added `resilience4j-spring-boot3` with
  `@Retry`/`@CircuitBreaker` on both the static-data and availability clients. Required
  also adding `spring-boot-starter-aspectj` (needed for the annotations to actually take
  effect via Spring AOP) — without it they were silently inert.
- Removed the stale "no resilience" note from `CLAUDE.md` once the reversal was confirmed.

## Step 1 — Coordinate transform

- HDB's dataset publishes locations in **SVY21** (a Singapore-specific projected
  coordinate system), not WGS84 lat/lon — confirmed by downloading the real dataset and
  inspecting columns (`x_coord`, `y_coord`) before writing any code.
- Verified the SVY21→WGS84 formula against a public reference implementation, then
  **independently re-implemented it in Python** to generate expected test values (so
  tests catch a transcription bug in the Java port, not just agree with themselves).
- Also added `HaversineDistanceCalculator`, tested against mathematically exact reference
  distances (e.g. equator-to-pole = a quarter of Earth's circumference).

## Step 2 — Static data pipeline

- Confirmed the real dataset's addresses contain unescaped commas inside RFC4180 quotes
  → used Apache Commons CSV instead of naive string-splitting.
- Built a live fetch (poll-download → pre-signed S3 URL → CSV) with a **bundled CSV
  fallback** on any failure, so the app never boots with an empty car park table.
- **Bug found & fixed**: `RestClient.uri(String)` was re-encoding the pre-signed S3 URL's
  already-percent-encoded signature, breaking the download with `InvalidToken`. Fixed by
  passing a pre-built `URI` instead of a template string. Found by actually running the
  app against the real data.gov.sg endpoints, not by reading the code.
- **Bug found & fixed**: `RestClient.Builder` wasn't auto-configured as a bean in this
  Spring Boot 4.1.1 setup — added an explicit `@Bean` in `HttpClientConfig`.

## Step 3 — Live availability integration

- Store live data in its own table (`car_park_availability`), no foreign key to
  `car_park` — the availability feed covers HDB+URA+LTA car parks, the static dataset is
  HDB-only, so unmatched rows are expected, not a bug.
- Sync logic: plain `try/catch` around the (resilience4j-wrapped) client call — on
  failure, do nothing to the database rather than clearing/overwriting it, so the last
  successfully-fetched data keeps serving until the next successful poll.
- The API's `update_datetime` field has no timezone offset — confirmed it's local
  Singapore time (not UTC), parsed explicitly against `Asia/Singapore`.
- **Bug found & fixed** (the most subtle one of the whole build): timestamp columns were
  plain `TIMESTAMP` (no timezone). This compiled, passed every test, and even looked
  correct in a same-machine run — only caught by manually reading raw `psql` output and
  noticing `fetched_at` didn't match actual UTC wall-clock time. A `TIMESTAMP` column only
  round-trips correctly if every JVM touching it shares the same default timezone, which
  isn't guaranteed once deployed in a container. Fixed by switching to `TIMESTAMPTZ`.

## Step 4 — Nearby search API

- Only `lot_type = 'C'` (regular cars) is searched; other lot types are stored but not
  surfaced by this endpoint.
- Added a default search radius (3km, configurable, capped at 50km) rather than just
  "sorted, take the top N" — without a cap, an empty area would still return the
  *nearest* car park mislabeled as "nearby," even if it's far away.
- Stale results (older than `stale-after-minutes`) are **flagged, not hidden** — a
  20-minute-old count is still more useful than no answer at all.
- **Bug found & fixed**: adding `@Validated` to the controller class routed constraint
  violations through an older validation path, causing out-of-range parameters to return
  raw `500`s instead of `400`s. Removed it — Spring MVC validates `@RequestParam` natively
  without it.
- **Bug found & fixed**: a custom `@RestControllerAdvice` wasn't taking effect because
  Boot's built-in `ProblemDetailsExceptionHandler` was silently winning for the same
  exception type. Fixed with `@Order(Ordered.HIGHEST_PRECEDENCE)`.

## Step 5 — Dockerization

- Multi-stage `Dockerfile` (JDK 21 to build, slim JRE 21 Alpine to run, non-root user).
- `docker-compose.yml`: Postgres with a healthcheck gating app startup order.
- **Deliberately did not publish Postgres's port to the host** in the committed file, to
  avoid port conflicts on a machine that might already run Postgres on 5432.
- Verified with a real `docker compose build && up` from a clean state, then curled the
  API from the host — not just "should work."

## Step 6 — Docs

- Wrote `README.md`, `DESIGN.md`, `AI.md`, cross-checking every documented endpoint
  parameter, field name, and config value against the actual code (not from memory).
- `AI.md` is written honestly, including the TIMESTAMPTZ bug as the primary "agent
  produced something subtly wrong" example, plus what was deliberately not delegated
  (architecture calls, git commits, installing software).

## After the six steps

- Rewrote `CLAUDE.md` to add three new sections for a fresh session to pick up the project
  without rediscovery: **Project state** (what's built, that nothing's committed yet),
  **Local environment** (the JDK 21 path issue and exact commands), and **Known gotchas**
  (the real bugs above, condensed). Nothing existing in `CLAUDE.md` was removed or altered.
- Built a separate "Parking Finder Internals" reference (published as a Claude Artifact) —
  a flowchart-and-class-by-class explanation of the whole codebase, verified against the
  live source rather than written from memory.
- Exposed Postgres's port (`5432:5432`) in `docker-compose.yml` so a locally-installed
  pgAdmin can connect (`localhost:5432`, db/user/password all `parking`) — **chosen to
  keep this in the committed file** rather than reverting it later. *Open follow-up, not
  yet done: add a one-line README note that port 5432 must be free on the host machine.*

## Persistence migrated: JdbcTemplate → Spring Data JPA

- **Requested explicitly**: switch from `NamedParameterJdbcTemplate` DAOs to Spring Data
  JPA repositories with real `@Entity` classes.
- Replaced the `CarPark`/`CarParkAvailability` domain records with `CarParkEntity` /
  `CarParkAvailabilityEntity` (the latter using an `@EmbeddedId` composite key,
  `CarParkAvailabilityId`, for `(car_park_no, lot_type)`). `CarParkRepository` and
  `CarParkAvailabilityRepository` are now plain `JpaRepository` interfaces with no custom
  code at all.
- **Upsert strategy — asked, not assumed**: since both entities use natural (pre-assigned,
  not generated) IDs, Spring Data can't use "ID is null" to detect a new row, so
  `saveAll()` does a `SELECT`-then-`INSERT`/`UPDATE` per row instead of one round-trip
  upsert. Given the choice between plain `saveAll()` (idiomatic, simpler) or a native
  `@Modifying` upsert query wrapped in a repository method (keeps single-round-trip
  efficiency, still "raw SQL" underneath), **chose plain `saveAll()`** — the extra
  round-trip is negligible at this data volume (a few thousand rows, polled once a
  minute, all local/same-network Postgres).
- The nearby-search join (no FK between the two tables, by design) became a JPQL query
  with an explicit `JOIN ... ON` between otherwise-unrelated entities, projected directly
  into the existing `CarParkAvailabilitySnapshot` record via a constructor expression.
- **Bug found & fixed** (caught by `spring.jpa.hibernate.ddl-auto: validate`, which checks
  entity mappings against `schema.sql` at startup rather than trusting them): `gantry_height`
  was `NUMERIC(5,2)` in the schema but mapped as a Java `double`, which Hibernate expects
  as `FLOAT`/`DOUBLE PRECISION`. The app refused to start with a clear error rather than
  silently misreading the column. Fixed by relaxing the schema column to `DOUBLE PRECISION`.
- Verified against a real Postgres exactly as before: full app run, real API calls through
  the new JPQL join, and a second full run to confirm `saveAll()` is idempotent (same row
  counts, no duplicate-key errors) — not just "compiles and tests pass."
- Updated `DESIGN.md`'s and `README.md`'s storage sections, which previously argued *for*
  plain JDBC over JPA — that reasoning was rewritten, not left contradicting the code.
- **New open item**: the published "Parking Finder Internals" Claude Artifact still
  describes the pre-migration JdbcTemplate design and the now-deleted `CarPark`/
  `CarParkAvailability` records. Not yet refreshed.

## Cleanup: two orphaned record files

- Asked directly whether the old domain records were still needed post-migration.
  Re-checking (rather than trusting the earlier migration's own claim) found `CarPark.java`
  and `CarParkAvailability.java` were **still on disk** — an earlier `rm` had silently done
  nothing (shell aliasing/interactive-prompt issue, no stdin to confirm against), and the
  tool output looked enough like success that it went unnoticed at the time. Verified both
  were fully unreferenced, then actually deleted them (`command rm -f`, bypassing whatever
  swallowed the first attempt) and reran the full test suite to confirm nothing broke.
- Every other record in the codebase was checked and kept — none of them represent a DB
  row (external API DTOs, pure coordinate value types, and the JPQL projection/response
  shapes), so none needed to become an entity.

## Finalizing the docs

- Consolidated **every command used across the whole build** (Docker Compose lifecycle,
  database access, local dev, debugging — both IDE and command-line JDWP, API testing)
  into one canonical **README.md "Commands reference" section**, rather than leaving them
  scattered across chat history. `CLAUDE.md`'s local-environment notes now point to that
  section instead of duplicating it, to avoid the two drifting apart later.
- Closed the previously-open port-5432 follow-up: README now explicitly documents that
  Postgres's published port means the stack won't start if a local Postgres already holds
  5432, and how to work around it.
- Added the JPA migration's `saveAll()`-vs-native-upsert trade-off, and the orphaned-record
  cleanup above, to `AI.md` as further concrete examples of the delegation/verification
  pattern — including the `rm` incident as an example of not even trusting the agent's own
  "done" claims about its actions, not just its code.

## Package reorganization (done outside this chat)

- Entities (`CarParkEntity`, `CarParkAvailabilityEntity`, `CarParkAvailabilityId`) moved
  from the `carpark`/`availability` packages into a new `com.carpark.singapore.entities`
  package; the two exceptions (`AvailabilityFetchException`,
  `StaticDatasetUnavailableException`) moved into a new `com.carpark.singapore.exceptions`
  package. Done directly in the IDE while a chat session was mid-edit on an unrelated
  artifact update — noticed via files changing on disk outside any requested action,
  investigated rather than assumed, and confirmed complete/consistent before continuing
  other work. No doc changes were actually needed for this — every MD file already
  referred to these classes by bare name, not fully-qualified path.

## Naming review: `NearbyCarParkRepository` → `NearbyCarParkQuery`

- Questioned directly: is this actually a repository? On inspection, no — it manages no
  entity's lifecycle (that's `CarParkRepository`/`CarParkAvailabilityRepository`'s job),
  joins two otherwise-unrelated entities, and returns a read-only projection
  (`CarParkAvailabilitySnapshot`), not a persisted type. It only extends Spring Data's
  bare `Repository<>` marker as a technical requirement to get a proxy, which isn't what
  the class is actually *for*. **Renamed to `NearbyCarParkQuery`** — a Query Object is a
  more honest description of "one specific question the nearby-search feature asks."
- Constructor/field in `NearbyCarParkService` renamed from `repository` to `query` to
  match; the test's mock renamed the same way.
- **Left as an open cleanup item**: the old `NearbyCarParkRepository.java` file could not
  be deleted from this chat session (both `./mvnw compile` and `rm` were denied by
  permissions, likely to avoid conflicting with a build/IDE session running concurrently).
  It's now dead code — nothing references it — but still needs manual removal.

## Custom Docker build command, and a real bug it surfaced

- **Requested**: a way to customize the Docker build. Clarified into something concrete —
  a `SKIP_TESTS` build arg on the `app` service (default `true`, matching prior behavior;
  `false` actually runs tests during the image build and fails it if they fail).
- **This immediately surfaced a real bug**: with `SKIP_TESTS=false`, the `app` image build
  failed outright. Root cause: `SingaporeApplicationTests` (a `@SpringBootTest` needing a
  live Postgres) ran as part of the full suite, but `docker build` has no database
  reachable — containers only get networked together at `docker compose up`, not during an
  image build. It would fail this way every time, regardless of whether the other 26 tests
  passed. **Fixed** by excluding `SingaporeApplicationTests` from the `app` build's test
  run specifically (`-Dtest=!SingaporeApplicationTests`), consistent with how it's been
  excluded from every other "quick" test run throughout this project.
- **You independently added a `test` service** to `docker-compose.yml` (while I was mid-
  investigation of the count discrepancy that led to finding the bug above) — it runs
  `./mvnw test` as a container *command* after `depends_on: postgres` has already brought
  Postgres up and networked it, so `SingaporeApplicationTests` can actually connect. This
  is the correct place for the full suite including DB-dependent tests; kept as-is, not
  reverted.
- **Asked directly**: should `test` run on every `docker compose up`, or only on demand
  (via Compose `profiles`)? **Chose: run every time** — simpler mental model ("one command
  verifies everything"), accepting the trade-off of a slower default startup and a third,
  short-lived container appearing in `docker compose ps`. Updated README's "Running it"
  and Commands reference to describe three containers, not two, and to correct the
  `SKIP_TESTS=false` description (it does NOT run the full suite — only the DB-independent
  part; the `test` service is what runs everything).
- This whole thread started because I reported "26 tests" was confirmed correct by
  re-reading current file contents, but couldn't explain the user's observed "21" without
  seeing exactly how it was run — asking for the precise source (rather than guessing)
  is what surfaced the actual build log and the real bug in it.

## Still open as of this file

- **Nothing has been committed to git yet.** The entire build above is one long
  uncommitted working tree. Per `CLAUDE.md`'s pre-commit review rule, the next step is a
  naming/design/refactoring review pass before committing in logical steps (roughly one
  per numbered step above), not a single squashed commit.
- **`src/main/java/com/carpark/singapore/search/NearbyCarParkRepository.java` needs to be
  manually deleted** — superseded by `NearbyCarParkQuery.java`, this chat session couldn't
  remove it itself (see above).
- The "Parking Finder Internals" Claude Artifact still needs a refresh to match the JPA
  migration (it documents the deleted JdbcTemplate-based repositories and records) — and
  now also needs the `NearbyCarParkQuery` rename once that refresh happens.
- DESIGN.md's own "what I'd improve" list (whole-system staleness signal, scheduled
  refresh for the static dataset instead of only-on-restart, full pagination, PostGIS at
  larger scale) is unimplemented by design — documented as deprioritized, not forgotten.
