# AI.md

## Setup

I worked entirely inside Claude Code, with a `CLAUDE.md` at the repo root as the only
persistent context/rules file (no separate `AGENTS.md`, no custom skills). It covers three
things:

1. **A build-failure protocol** — on any failed build, list every distinct root cause as
   separate bullets with a plain-English "why," rather than collapsing everything into
   "build failed," and propose fixes before applying them rather than guessing silently.
2. **A response process** — always show reasoning before making changes, and a "Clean Code"
   convention (small functions, 0-2 args, no magic numbers/booleans-as-flags, guard clauses
   over nested ifs, no duplicated logic, no dead code, boy-scout cleanup surfaced as
   suggestions rather than applied unasked).
3. **A pre-commit review requirement** — naming, design-pattern fit, and refactoring
   opportunities get surfaced (not silently applied) before any commit.

I also pasted the full project brief into `CLAUDE.md` up front, so the agent had the
complete requirements in context for every subsequent turn without me re-explaining
scope each time.

## How I split the work

The single most important thing I did was refuse to let the agent build this in one shot.
My first substantive instruction, after it had already started scaffolding a full plan, was:
*"Instead of building the complete project in a single go, tell me the steps in which the
project needs to be built and we will build it step by step."* It proposed six steps
(coordinate transform → static data → live availability → nearby search API →
Dockerization → docs), and I approved and gated each one individually ("start with step 1
and stop when completed"), reviewing the summary of each before saying "yes" to the next.

Within that structure, I delegated **all actual writing** — code, tests, Dockerfile,
these docs — to the agent. What I kept for myself was every decision with a real
trade-off attached:

- **Scope of the availability integration.** When it flagged that "just build the
  availability API" had no static data to attach to, it gave me three concrete options
  (full pipeline / availability-only with a bare table / in-memory stub) rather than
  picking one. I chose the full pipeline, since a stub wouldn't actually prove anything end
  to end.
- **Whether to use a resilience library at all.** It initially added resilience4j
  (retry + circuit breaker) unprompted, matching some config it found already sitting in
  `application.yml`. I stopped it — I wanted the *minimum* needed to satisfy the
  requirement, not a framework by default. It rebuilt the client with a plain
  timeout + single attempt + fallback instead. Later, once I'd seen the rest of the system
  take shape, I changed my mind and asked for resilience4j back after all, since the
  config was already there for a reason and the retry/circuit-breaker semantics were
  genuinely worth having for a flaky government API. Both times, it didn't just do the
  opposite silently — it explained the trade-off it was choosing on my behalf before doing it.
- **Local toolchain vs. Docker-only verification.** When the build failed for lack of a
  local JDK 21, it asked whether to install one or verify exclusively through Docker. I
  installed the JDK myself, specifically so I could get fast local test feedback while
  reviewing its work, rather than round-tripping through a full Docker build every change.
- **Switching persistence from JdbcTemplate to Spring Data JPA**, after the initial build
  was already working. This was my call, made after seeing the hand-written SQL in
  practice — I wanted real `@Entity` classes and repository interfaces instead. Within
  that, the agent surfaced one more trade-off rather than picking silently: since both
  tables use natural, pre-assigned IDs, Spring Data JPA can't do a single-round-trip
  upsert the way the old SQL did — it does a `SELECT`-then-`INSERT`/`UPDATE` per row. I
  chose plain, idiomatic `saveAll()` over a native-SQL upsert query wrapped in a
  repository method, since the extra round-trip is irrelevant at this data volume.

I did not personally read every line of every file as it was written. What I did instead
is described below.

## How I verified the output

Verifying behaviour matters more than trusting generated code, so I leaned much more
heavily on **running the real thing** than on reading source line by line:

- **Independent-implementation cross-checks**, not just plausible-looking tests. For the
  SVY21→WGS84 coordinate transform (a part known to be error-prone), the agent
  didn't write a Java implementation and then write tests that just confirm the Java
  agrees with itself — it independently reimplemented the same published formula in
  Python first, and used *those* numbers as the expected test values. That's a
  meaningfully stronger check: a transcription bug in the Java port would actually fail a
  test, instead of the test and the bug both being wrong in the same way.
- **Full-dataset assertions, not just a couple of hand-picked examples.** One test runs
  the coordinate transform over the *entire* bundled dataset (2,270 real car parks) and
  asserts every single result lands inside Singapore's bounding box — catching the class
  of bug (e.g. swapped easting/northing) that two or three cherry-picked examples might
  miss by coincidence.
- **Real infrastructure, not mocks, at every step.** After each step, we spun up a
  disposable Postgres container, ran the actual app against it, hit the *real*
  data.gov.sg endpoints (not stubs), inspected raw rows with `psql`, then tore the
  container down. The final step ran a clean `docker compose build && up` and curled the
  API from the host, exactly as anyone running this for the first time would.
- **The unit test suite** (26 tests by the end) — but I treated it as necessary, not
  sufficient, given how many of the real bugs below it structurally could not have caught.

## Where the agent produced something subtly wrong

The clearest example: timestamps were declared as plain Postgres `TIMESTAMP` (no
timezone). This **compiled, passed every test, and even looked correct in a live run** —
on my machine, writing an `Instant` and reading it back gave the right value every time.
The bug was invisible until I manually inspected the raw stored rows with `psql` and
noticed `fetched_at` didn't match actual UTC wall-clock time — it had been silently
round-tripped through the JVM's *default* timezone (IST, on my laptop) instead of UTC. That
round-trip only stays correct as long as every JVM that ever touches the column shares the
same default zone, which is not guaranteed once this runs in a container. Since every
staleness calculation in this system is `now - lastUpdated`, this bug wouldn't have thrown
an exception anywhere — it would have silently shifted every "stale" flag by however many
hours the container's default timezone happened to differ from mine, in production, with
no test ever failing. I caught it by deliberately reading the data back out, not by
trusting a green test suite, and the fix (switch to `TIMESTAMPTZ`) is documented in
DESIGN.md along with why it matters.

Two smaller cases in the same family, both only found by actually running the app rather
than reading the code: `@Retry`/`@CircuitBreaker` annotations were completely inert (no
error, no retry, exception just passed straight through) until an AOP-enabling dependency
was added — invisible to unit tests because they mock the client directly and never
exercise the Spring proxy. And adding `@Validated` to the REST controller — the
textbook-looking way to enable request validation — actually made out-of-range parameters
return raw `500`s instead of `400`s, because it silently switched Spring onto an older
validation code path that doesn't auto-translate to a proper HTTP error. Neither of these
would show up from reading the code; both only surfaced from curling the running API with
bad input and checking the actual status code.

One more, smaller but worth including precisely because it's mundane: during the JPA
migration, the agent ran `rm` on two now-unused files and reported them removed. They
weren't — the shell's `rm` was silently swallowed (an aliased/interactive variant with no
stdin to confirm against), so the command did nothing, but the tool output looked enough
like success that the agent moved on without checking. It surfaced a session later when I
asked whether the old records were still needed — re-checking the filesystem showed both
files still there, unreferenced, dead. Nothing dramatic, but a good reminder that even an
agent's own "done" claims about its own actions are worth spot-checking, not just its
code's behavior.

## What I didn't trust the agent with

- **Any product or architecture trade-off** — what to build next, whether to add a
  resilience library, how to handle stale data, what "nearby" should default to. The
  agent proposed options and reasoning; I made the call every time, and it never
  proceeded past an ambiguous fork without asking.
- **Git commits.** As of writing this file, nothing has been committed yet — the six build
  steps were done as an uncommitted working sequence, and `CLAUDE.md`'s pre-commit review
  requirement (surface naming/design/refactor feedback, wait for confirmation) runs before
  anything is committed. I did not let the agent decide when or how to commit on its own.
- **Installing software or mutating my machine's state** — it asked before installing a
  JDK, and used disposable, explicitly-torn-down Docker containers for verification rather
  than anything persistent.
