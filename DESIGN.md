# Design Notes

## Data sources and the coordinate problem

The HDB car park dataset (`d_23f946fa557947f93a8043bbef41dd09`) publishes `x_coord`/`y_coord`
in **SVY21** (EPSG:3414) — Singapore's own projected coordinate system, in metres from a
local false origin — not latitude/longitude. This is easy to miss at a glance (the values
look like ordinary numbers, e.g. `30314.7936, 31490.4942`), and using them as-is would put
every car park roughly 6,300km off the coast of Africa (0°N, 0°E is the WGS84 origin, and
SVY21's numbers are nowhere near lat/lon ranges) — so the transform isn't optional.

I converted every row to WGS84 at load time using the inverse transverse-Mercator formula
(the same one published by Singapore's SLA for this exact projection), rather than storing
raw SVY21 and converting per-request — it's a fixed, one-time transform per car park, and
downstream code (distance calculation, the API response) should only ever deal in one
coordinate system.

**Verification approach:** this is flagged in the brief as one of the parts most likely to
break, so I didn't just trust the Java port. I independently reimplemented the same
published formula in Python and used *those* outputs as the expected values in the Java
unit tests — so the tests catch a transcription bug in the Java code, rather than just
confirming the code agrees with itself. I also ran the transform over the *entire* bundled
dataset (2,270 rows) in a test and asserted every resulting point falls within Singapore's
bounding box, which catches gross errors (e.g. swapped easting/northing) that a couple of
hand-picked examples might miss.

## Data storage

PostgreSQL via Spring Data JPA (Hibernate). Two entities, two tables:

- `CarParkEntity` → `car_park` — one row per HDB car park (from the static dataset), keyed
  by `car_park_no`.
- `CarParkAvailabilityEntity` → `car_park_availability` — one row per `(car_park_no,
  lot_type)`, upserted on every poll, keyed by an `@EmbeddedId` composite key
  (`CarParkAvailabilityId`) since there's no single natural single-column key.

**No foreign key** between the two tables: the availability feed covers HDB, URA, and LTA
car parks, while the static dataset is HDB-only. A meaningful fraction of availability rows
(about 8 of 2,422 in a live sample I checked) don't match anything in `car_park` — that's
expected, not a data-quality bug, and an FK would just make ingestion brittle. Nearby search
uses an inner join, which naturally excludes those non-HDB rows (fine, since we have no
address or location for them anyway). This same absence of an entity-level relationship
means the nearby-search join can't be expressed as a normal JPA association traversal —
it's a JPQL query with an explicit `JOIN ... ON` between two otherwise-unrelated entities,
projected straight into a `CarParkAvailabilitySnapshot` record via a constructor expression.

That query lives in `NearbyCarParkQuery`, deliberately not named `...Repository` — it
manages no entity's lifecycle (that's `CarParkRepository`/`CarParkAvailabilityRepository`'s
job), and it returns a read-only projection rather than a persisted type. It only extends
Spring Data's bare `Repository<>` marker interface because that's required to get a proxy
at all; the name should describe what the class is *for* (one specific question the
nearby-search feature asks), not the technical machinery it happens to sit on.

`schema.sql` is the source of truth for the schema (not Hibernate-generated DDL) —
`spring.jpa.hibernate.ddl-auto: validate` only checks the entity mappings against it at
startup, catching a mismatch immediately rather than serving requests against a
misunderstood column. That check actually caught a real one while building this: `gantry_height`
was `NUMERIC(5,2)` in the schema but mapped as a Java `double`, which Hibernate expects as
`FLOAT`/`DOUBLE PRECISION` — the app refused to start with a clear error instead of silently
truncating or misreading the column. Fixed by relaxing the column to `DOUBLE PRECISION`
(gantry height doesn't need fixed decimal precision).

**Upserts use plain `JpaRepository.saveAll()`**, not a hand-rolled `INSERT ... ON CONFLICT`.
Because both entities use natural, pre-assigned IDs (not generated ones), Spring Data
can't tell "new" from "existing" by checking for a null ID — it treats every save as a
potential update and issues a `SELECT` before each `INSERT`/`UPDATE` (via JPA's `merge()`
semantics), rather than a single round-trip upsert statement. At this system's actual scale
(2,270 static rows once at startup, ~2,400 availability rows once a minute, all against a
local/same-network Postgres) that extra round-trip per row is not something a user or the
poll cycle would ever notice; `hibernate.jdbc.batch_size`/`order_inserts`/`order_updates`
are enabled to batch the JDBC traffic regardless. I'd reconsider this if the poll volume or
row count grew by an order of magnitude or more — at that point a native upsert query
declared on the repository interface (still JPA, just one method backed by raw SQL) would
be the fix, not a return to hand-written JDBC everywhere.

## Resilience and reconciliation (the open-ended part)

Both external calls — the static dataset's poll-download+S3 fetch, and the live
availability API — go through the same pattern:

1. **`@Retry`** (resilience4j): a few attempts with exponential backoff, for transient
   failures (timeout, momentary 5xx, a dataset export job not being ready yet).
2. **`@CircuitBreaker`**: once a call is clearly failing repeatedly, stop hammering the
   upstream API for a cooldown window rather than retrying forever.
3. **A fallback that raises a typed exception** (`AvailabilityFetchException` /
   falls back to the bundled CSV) rather than the client silently returning an empty
   result — the *business* decision of what to do about a failure (keep serving old data
   vs. failing startup) is made explicitly by the caller, not hidden inside the HTTP client.

For live availability specifically: `CarParkAvailabilitySyncService.sync()` wraps the
client call in a plain `try/catch`. On failure, it logs a warning and **does nothing else**
— the existing rows in `car_park_availability` are left untouched. There's no separate
"stale data" table or flag write; staleness is derived entirely at read time, from
`lot_updated_at` vs. now vs. `stale-after-minutes` (default 15 min). This means:

- **Reconciliation is just the next successful poll.** Because every sync is a full
  upsert keyed by `(car_park_no, lot_type)`, the moment a fetch succeeds again, every row
  it returns overwrites the old value. There's no separate reconciliation pass to write —
  the normal ingestion path *is* the reconciliation path.
- **A live API returning stale data** (a 200 OK where some individual car parks haven't
  actually updated in a while) is handled the same way as an outright failure: it's not
  something the client can detect (the API doesn't say "this is stale"), so it surfaces at
  read time as `stale: true` on the affected result, computed independently per car park.
- **I chose to show stale results rather than hide them.** A car park's last-known count
  from 20 minutes ago is still much more useful to a driver than no information at all;
  hiding it converts "slightly outdated" into "looks like there's no parking here," which
  is a worse user experience for a small accuracy gain.
- One thing I did **not** build, given the time-box: a metric/alert for "the last N polls
  have all failed" (i.e., whole-system staleness, not per-car-park). Right now that's only
  visible in logs. If most/all rows are stale, every result the API returns will correctly
  show `stale: true`, so the API is honest about it — it's just not paged on anywhere yet.

### A concrete bug this design caught

While verifying step 3, I found that timestamps were declared as plain `TIMESTAMP`
(no timezone) in Postgres. That's exactly the "stale data" surface described above, and it
was silently wrong: writing a UTC `Instant` into a `TIMESTAMP WITHOUT TIME ZONE` column
round-trips correctly only as long as every JVM that ever touches the column has the same
default timezone — true on my laptop, not guaranteed once this runs in a container. I only
caught it by reading the raw rows back with `psql` and noticing `fetched_at` didn't match
actual UTC wall-clock time. Fixed by switching both timestamp columns to `TIMESTAMPTZ`,
which stores an absolute instant regardless of any session's timezone. Since staleness math
is `now - lot_updated_at`, a timezone bug here wouldn't have thrown an exception or failed
a unit test — it would have just silently marked fresh data as stale (or vice versa) by
however many hours off the container's default zone happened to be. This is the class of
bug the "handling of stale... data" requirement is really pointing at, and I'm glad I
manually inspected raw data instead of only trusting green tests.

## Product decisions

**Which lot type counts as "parking"?** The live feed reports multiple lot types per car
park (`C` = car, `H` = heavy vehicle, `Y` = motorcycle, plus a couple of rarer codes).
"Find available parking" for a general user means cars, so the search only considers
`lot_type = 'C'`. This is stored at the API layer, not at ingestion — `car_park_availability`
keeps every lot type, in case a future endpoint wants motorcycle/heavy-vehicle availability.

**A default search radius, not just "sorted, take the top N."** Sorting by distance and
capping at `limit` is not the same as "nearby" — without a radius cap, a request from an
empty part of the map would still return Singapore's closest car park, mislabeled as
"nearby" when it might be 20+km away. Default `radiusKm=3` (tunable per request, capped at
50 — roughly Singapore's own extent) means "no results" is an honest answer when there's
nothing genuinely close, instead of a misleading one. This is also the direct fix for the
"nearby results 10km away" question below.

**Volume of results.** `limit` (default 20, max 100) plus the radius cap keeps a normal
response small and relevant, sorted nearest-first so truncation never drops something
closer than what's shown. I deliberately did not build full pagination (offset/cursor) —
for a "find parking near me" use case, a capped top-N nearest list is the actual product
need (nobody wants page 4 of nearby car parks); I'd revisit this if a use case emerged for
"show me everything within 20km," where a real 2,000-row response would need paging.

## What I'd change for a different scale/context

- **Distance calculation moves into SQL/PostGIS.** Right now the service loads every
  car park with an available lot (a few thousand rows) into memory and computes haversine
  distance in Java — fine at Singapore's scale, but wouldn't scale to, say, all of
  Southeast Asia. At that point I'd add PostGIS, store `location GEOGRAPHY(POINT)`, index
  it, and push the radius filter + `ORDER BY ST_Distance` into the query itself.
- **The static dataset's live refresh** currently retries and falls back to a bundled
  snapshot on every restart. At larger scale I'd separate this into its own scheduled job
  (like availability already is) so a long-running instance also picks up new/closed car
  parks without a restart, rather than only refreshing at startup.
- **Multi-instance deployments** would need the availability poller to run as a singleton
  (e.g. a leader-elected job, or moved to an external scheduler) rather than one
  `@Scheduled` task per instance hammering the upstream API redundantly.

## Reflections

**Most challenging decision:** deciding how much resilience machinery to add, and where.
It was tempting to wrap every external call in the same retry/circuit-breaker/fallback
pattern by default, but that's exactly the kind of thing that's easy to over-build in a
timeboxed exercise — more moving parts than the actual failure modes justify. The version
that shipped keeps that machinery to the two places it earns its keep (the two live HTTP
calls), keeps the *decision* about what to do on failure at the business layer (a plain
`try/catch` in the sync service) rather than buried in a client, and skips it everywhere
else (e.g. no retry around the DB itself, which Spring/HikariCP already handle sensibly).

**If users report "nearby" results 10km away:** first, check whether it's a *sorting* bug
or a *radius* bug — those look identical to a user but are different code paths. I'd:
1. Reproduce with the exact reported lat/lon and inspect the raw response — is the 10km
   result the *closest* one returned (radius/filter problem) or is it sorted out of order
   behind something farther (sorting bug in `NearbyCarParkService`)?
2. Check whether `radiusKm` was passed explicitly (client bug: passing a huge radius) vs.
   using the 3km default (server bug: the filter not actually excluding it).
3. Verify the reported user coordinates aren't the actual bug — e.g. a client sending
   `lat`/`lon` swapped, or a stale/cached device location. Since the API is coordinate-in,
   coordinate-out, a swapped-argument bug on the client side would look exactly like "your
   nearby results are wrong" from the outside.
4. Check the car park's stored lat/lon — if it's an HDB car park, verify the SVY21→WGS84
   conversion for that specific `car_park_no` against an independent source; a transform
   bug for one/few car parks (vs. all of them) would show up as isolated wrong-looking
   results rather than a systemic offset.
5. Add a regression test pinning the reported (user location, expected nearest car park)
   pair once the root cause is found, so it can't silently regress.

**One thing I'd improve given more time:** a whole-system staleness signal (see above) —
right now "the last 5 polls all failed" is only visible in application logs, not surfaced
through the API or health endpoint. I'd add a lightweight indicator (e.g. an
`/actuator/health` custom indicator, or a `dataFreshness` field on the API response) so a
consuming client can distinguish "no parking nearby" from "our data pipeline has been down
for an hour" — right now both look identical from outside the system.

## What I deprioritized, and why

- **Whole-system staleness/alerting** (above) — per-record staleness covers the assignment's
  explicit requirement; system-level monitoring is a real gap but a smaller one, and fitting
  the exercise's time-box meant choosing where to stop.
- **Full pagination** for `/nearby` — a capped, sorted top-N response is the right shape for
  the actual use case (see "Volume of results" above); true pagination isn't built.
- **PostGIS/spatial indexing** — unnecessary at Singapore's scale (~2,000 car parks); the
  in-memory haversine approach is simpler and was worth keeping simple for this exercise.
- **A background job to refresh the static dataset without a restart** — it currently
  refreshes on every app start, which is enough for a car park list that changes rarely.
