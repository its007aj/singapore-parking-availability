## On build failure
If any build step fails, do not just report "build failed." Instead:
- Read the full error/stack trace output, not just the last line.
- List every distinct reason the build failed, as separate bullet points — don't collapse multiple root causes into one summary line.
- For each reason, explain *why* it happened in plain terms (e.g. missing dependency, version mismatch, upstream repo not built yet, stale cache, compilation error in a specific file/line).
- If the failure looks caused by skipping the upstream repo build order above, say so explicitly and point to which repo(s) likely need to be built first.
- Do not guess at a fix and apply it silently — present the reasons first, then propose fixes, before making changes.

## Entering multi-line prompts (Claude Code terminal)

Pressing Enter submits the message immediately, so use one of these to insert a line break instead of submitting:

- `\` followed by Enter — backslash then newline (classic shell line-continuation)
- `Option+Enter` (Mac) or `Alt+Enter` (Windows/Linux) — inserts a newline without submitting
- `Shift+Enter` — works in some terminal emulators, but not universally (depends on whether the terminal app forwards the modified Enter keycode)
- Pasting multi-line text directly — a pasted block that already contains newlines works fine and only submits on a trailing Enter

This matters for the untagged shorthand below, which depends on line breaks to separate task/context/question.

### Tagged format
When the user gives a multi-part task, they may structure it using descriptive XML tags instead of spelling out each part in prose. Two equivalent naming conventions are used interchangeably:

<instructions>...</instructions>     <task>...</task>
<context>...</context>               <constraints>...</constraints>
<question>...</question>             <scope>...</scope>

- `<instructions>` / `<task>`: the task(s) to perform.
- `<context>` / `<constraints>`: background information, limitations, things to avoid, or rules to follow (e.g. no schema changes, reuse existing client).
- `<question>` / `<scope>`: either a specific question to answer, or the part of the codebase/files/modules to look at or touch — inferred from content, not just tag name.

Any subset of these tags may appear, in any order, and the two naming sets may be mixed if the user does so. Other descriptive tag names may also be used if they better fit the request — treat any XML tag by its name and content, not just the ones listed above.

### Untagged shorthand
If the user's message has no XML tags but consists of exactly three non-empty lines (separated by single line breaks, no blank lines between them), map them positionally in this fixed order:

1. Line 1 → task/instructions
2. Line 2 → context/constraints
3. Line 3 → question/scope

If the user's message doesn't fit this exact three-line pattern (fewer/more lines, blank lines, multi-sentence paragraphs, or a single block of prose), do NOT try to force a mapping — treat it as a normal, untagged prompt and respond to it as ordinary prose.
In all cases, treat these as structured input, not literal text to echo back — read each section for its role and respond accordingly, without asking the user to re-explain what the tags or line order mean.
When this shorthand is applied, briefly state which line was interpreted as task/context/question before proceeding, so the mapping is visible and can be corrected if wrong.

## Response process (every prompt)
- **Always show thought process first**, before making changes or giving a final answer. Explain the reasoning/approach, not just the conclusion — this applies to every task, not just complex ones.
- **Always follow the readability + refactoring convention** below when writing or editing code.
- **Proactively suggest refactoring opportunities** — whenever a function is changed, also look at:
    - the code immediately above/below the changed function, and
    - any function that calls it, or is called by it

  If refactoring opportunities are found there (even unrelated to the current task), surface them as suggestions — don't apply them automatically, just point them out with a brief reason, and let the user decide whether to act on them.

## Readability + refactoring convention (Clean Code)
- Functions should do one thing; if a function needs a comment to explain a section, that section is a candidate for extraction into its own well-named function.
- Function length: prefer under ~20-30 lines; long functions are a signal to split.
- Function arguments: prefer 0-2; avoid boolean flag arguments (usually means the function is doing two things).
- Names must reveal intent — no `data`, `temp`, `obj`, single-letter names (except loop indices).
- No magic numbers/strings — extract to named constants.
- Avoid deep nesting — prefer guard clauses / early returns over nested if/else.
- No duplicated logic — extract shared code into a reusable function.
- Remove dead code (commented-out blocks, unused imports/variables) rather than leaving it.
- Boy Scout Rule: when touching a function, leave surrounding code slightly cleaner than found — but only suggest such changes, don't apply unrelated refactors silently (see "Response process" above).


## Pre-commit review
Before committing any change, review the diff and provide suggestions covering:
- **Naming** — better names for functions, variables, interfaces, or classes, if the current ones don't clearly reveal intent (per the readability convention above).
- **Design patterns** — if the change could be restructured to follow an established design pattern (e.g. Strategy, Factory, Observer, Builder, Decorator, Template Method), point it out and briefly explain which pattern fits and why.
- **Refactoring opportunities** — per the "Proactively suggest refactoring opportunities" section above (surrounding code, callers/callees).

Present these as suggestions only — do not rename, restructure, or apply a design pattern automatically. Wait for the user to confirm before making any of these changes. This review happens before every commit (git commit is already gated behind "ask" in settings.json, so use that pause point to surface this review).

## Assignment Doc
Introduction Design and implement an API-only application that helps users find available parking near their location in Singapore. Your task is to build a solution in Java using real-world data sources. The specific technical approaches, API design, and architectural decisions are yours to make. We want to see how you think about product requirements and technical trade-offs. How we work. We are an AI-first team. We expect you to build this with AI coding tools, and we care as much about how you direct and verify the agent as about the finished code. Using AI is encouraged, not penalised. What we are really evaluating is your judgment: how you brief an agent, how you catch its mistakes, and how you prove the system works. Be honest and specific about all of this in your AI.md , which is a primary part of this submission. The Problem Build a system that: ● Loads car park information from a static dataset ● Fetches live availability data from an API ● Provides an API for users to find nearby available parking Data Sources 1. Car park information (static dataset) ● Dataset: https://beta.data.gov.sg/datasets/d_23f946fa557947f93a8043bbef41dd09/view ● Contains location and details for each car park ● Note: research the coordinate format and determine if transformation is needed 2. Car park availability (live API) ● Endpoint: https://api.data.gov.sg/v1/transport/carpark-availability ● Dataset info: https://beta.data.gov.sg/datasets/d_ca933a644e55d34fe21f28b8052fac63/view ● Your system should fetch and store this data, handling updates on subsequent runs
Requirements Your API should: ● Accept user coordinates and return nearby car parks with availability ● Sort results by proximity to user location ● Return only car parks with available lots ● Handle scenarios with many results (consider user experience) ● Implement input validation and error handling ● Use appropriate data storage solution(s) of your choice ● Handle a live API that can be slow, fail, rate-limit, or return stale data. Degrade gracefully and reconcile on the next run, and explain your approach in DESIGN.md . This mirrors real partner-integration work. Consider: what information would be most valuable to users? What edge cases should you handle? What assumptions are you making about API usage?
Deliverables 1. Working Code ● Runnable application in Java ● Use Git for version control as you would in regular work - we read your commit history, so commit in logical steps with clear messages rather than one squashed commit at the end. ● Must be packaged with Docker Compose . Your solution will be evaluated on different machines without Java/Maven installed, so reviewers must be able to run it using only Docker Compose. ● Include automated tests for the parts most likely to break: the coordinate transformation, the proximity logic, and the handling of stale or failed availability data. We want to see that you verify behaviour, not just trust generated code. 2. README.md ● How to run your application using Docker Compose (reviewers should not need to install Java/Maven/Gradle) ● API documentation (endpoints, parameters, example requests/responses) ● Overview of your solution architecture 3. DESIGN.md Document your design thinking and the reasoning behind your approach: trade-offs you considered, alternatives you evaluated, assumptions you made, and how your solution might evolve for different contexts or scales. Also reflect on: ● What was your most challenging technical or product decision, and why? ● If users report "nearby" results 10km away, how would you investigate and fix this? ● What's one aspect you'd improve given more time?
AI.md (required) We expect you to use AI coding tools, and this document is weighted alongside your code. Be specific and honest. Please cover: ● Your setup: any AGENTS.md , skills, rules, or reusable prompts and context you gave the agent. ● How you split the work between yourself and the agent, and how you decided what to delegate. ● How you verified the agent's output. Which checks or tests did you rely on instead of reading every line? (If for this task you chose to read every line, say so and why - that is a legitimate answer, not a wrong one.) ● At least one concrete case where the agent produced something subtly wrong or incomplete: how you noticed it, and how you fixed it. ● Anything you chose not to trust the agent with, and why.
Guidelines & Submission Scope & Time. Design a solution that fits within roughly 6-8 hours of work. This is intentional. We want to see how you prioritise and make trade-offs with limited time. The resilience/reconciliation behaviour above is the most open-ended part: if you run short on time, it is fine to document your intended approach in DESIGN.md and implement a simpler version - we would rather see a clear plan than a rushed implementation. Tell us in DESIGN.md what you deprioritised and why. You are welcome to spend more time if you choose, but we will also evaluate that decision-making. Timeline. Submit within 1 week. If you need more time due to commitments, contact your recruitment coordinator. A short follow-up. After we review your submission, the onsite includes a 20-30 minute live extension : you will make a small change to your own repository using your own AI setup while we watch. This is not a gotcha - it is the fastest way for us to see how you actually work with an agent. Come with your environment ready to run. Submission Process. Host your code on GitHub as a private repository and invite your assigned reviewers (your coordinator will provide the handles). ● Rajesh-wego ● ashu-wego ● nishantwego


## Project state (Singapore Parking Finder)

Read this before doing anything else — it's the living status of the build, so a new
session doesn't have to rediscover it.

**Build status: all 6 planned steps are done, plus one post-build migration.**
1. Coordinate transform (`geo` package: `Svy21ToWgs84Converter`, `HaversineDistanceCalculator`)
2. Static car park data pipeline (`carpark` package: CSV parsing, live+fallback client, DB loader)
3. Live availability integration (`availability` package: client, mapper, sync service, scheduler)
4. Nearby search REST API (`search` package + `web.ApiExceptionHandler`)
5. Dockerization (`Dockerfile`, `docker-compose.yml`) — verified working via a clean `docker compose build && up`
6. Docs (`README.md`, `DESIGN.md`, `AI.md`, plus `DECISION_LOG.md` as a working note) —
   written, cross-checked against the actual code, and kept current as the code changed
7. Persistence migrated from `NamedParameterJdbcTemplate` to Spring Data JPA (entities +
   repositories) — see the JPA gotcha below before touching any repository/entity code.
8. Entities/exceptions moved into their own packages (done via IDE refactor, not this
   chat): `CarParkEntity`, `CarParkAvailabilityEntity`, `CarParkAvailabilityId` now live
   in `com.carpark.singapore.entities`; `AvailabilityFetchException`,
   `StaticDatasetUnavailableException` now live in `com.carpark.singapore.exceptions`
   — not inside `carpark`/`availability` alongside the classes that use them.
9. The `search` package's join query was renamed `NearbyCarParkRepository` →
   `NearbyCarParkQuery` (it doesn't manage an entity's lifecycle or return a persisted
   type, so "Repository" was misleading — see DESIGN.md's Data storage section).
   **Cleanup still pending**: `src/main/java/com/carpark/singapore/search/
   NearbyCarParkRepository.java` is now dead code (nothing references it) but this chat
   session couldn't delete it — both `./mvnw compile` and `rm` were denied by permissions
   this session (likely to avoid conflicting with a build/IDE running concurrently).
   Delete it manually before committing.

26 unit tests pass (`./mvnw test`, excluding `SingaporeApplicationTests` — see below;
not re-verified after the rename above due to the permission denials just described —
run it yourself before trusting this count is still accurate).
Every command needed to build/run/test/debug this project is consolidated in
**README.md's "Commands reference" section** — check there first before re-deriving one.

**Git status: nothing has been committed yet.** The whole build above (steps 1–7) is one
long uncommitted working tree. The next step, when asked, is the "Pre-commit review" pass
above, then committing in logical steps (roughly one per numbered step above) rather than
a single squashed commit — the assignment explicitly says commit history is read.

**If asked to make a further change:** re-run the relevant tests and, for anything
touching the API/DB/Docker, re-verify with a real `docker compose up` or a temp Postgres
container rather than trusting a compile pass — see "Known gotchas" below for why. Also
verify file operations actually happened (e.g. `ls` after an `rm`) rather than trusting a
command's exit output — this bit us once already (see AI.md).

## Local environment for this project

Do not change the shell properties for this project, install whatever is needed for this
project locally.

Concretely, this means:
- Two JDKs are installed on this machine: Corretto 8 (the shell's default `JAVA_HOME`,
  left unchanged deliberately) and **Corretto 21** at
  `/Users/jain.aman/Library/Java/JavaVirtualMachines/corretto-21.0.12.1/Contents/Home`,
  installed specifically for this project. This project requires Java 21. Every Maven
  command needs `JAVA_HOME` exported to the JDK 21 path **inline for that command**, not
  set globally, e.g.:
  ```
  export JAVA_HOME=/Users/jain.aman/Library/Java/JavaVirtualMachines/corretto-21.0.12.1/Contents/Home
  ./mvnw -q -Dtest='!SingaporeApplicationTests' test
  ```
- Docker and Docker Compose are installed and confirmed working. Postgres's port
  (`5432`) is published to the host in `docker-compose.yml` (for pgAdmin/psql access),
  so only one Postgres — this project's or a pre-existing local one — can be running on
  that port at a time.
- **Full command reference lives in README.md's "Commands reference" section** — Docker
  Compose lifecycle, local dev, debugging (IDE and command-line JDWP), API testing. Don't
  re-derive these from scratch; that section is kept current as the canonical source.
  Quick pointer for the most common one: unit tests that don't need a database run via
  `./mvnw -q -Dtest='!SingaporeApplicationTests' test` (`SingaporeApplicationTests` is a
  `@SpringBootTest` that needs a live Postgres).

## Known gotchas (read before touching related code)

Each of these was a real bug caught during the build, not a hypothetical — re-reading
this list is faster than re-discovering them:

- **`@Retry`/`@CircuitBreaker` (resilience4j) need `spring-boot-starter-aspectj` on the
  classpath.** Spring Boot 4.1.1 renamed `spring-boot-starter-aop` to
  `spring-boot-starter-aspectj`. Without it, the annotations are silently inert — no
  error, no retry, the exception just passes straight through as if they weren't there.
- **Never add `@Validated` to a `@RestController` class.** It routes `@RequestParam`
  constraint violations through the older AOP validation path, which throws a raw
  `jakarta.validation.ConstraintViolationException` that surfaces as an unhandled `500`,
  not a `400`. Spring MVC (6.1+/Boot 3.2+) already validates `@RequestParam` constraints
  natively without any class-level annotation.
- **A custom `@RestControllerAdvice` needs `@Order(Ordered.HIGHEST_PRECEDENCE)`** to win
  over Boot's built-in `ProblemDetailsExceptionHandler` (auto-registered by
  `spring.mvc.problemdetails.enabled: true`), which otherwise silently handles the same
  exception types first with a generic message.
- **Any timestamp representing an absolute instant must be `TIMESTAMPTZ`, never plain
  `TIMESTAMP`.** A `TIMESTAMP WITHOUT TIME ZONE` column round-trips correctly only if
  every JVM touching it shares the same default timezone — this fails silently (compiles,
  passes tests, even looks right in a same-machine run) and only breaks once the app runs
  somewhere with a different default zone, which is exactly what happens in a container.
- **`RestClient.get().uri(String)` re-encodes already-percent-encoded query strings.**
  This breaks AWS pre-signed URLs, whose signature is byte-exact. Use
  `URI.create(rawUrl)` and `.uri(URI)` instead whenever the URL is already fully encoded.
- **`RestClient.Builder` is not auto-configured as a bean** in this Spring Boot 4.1.1
  setup — it's provided explicitly via `com.carpark.singapore.config.HttpClientConfig`.
  Don't assume it exists without checking.
- **The live availability feed covers HDB+URA+LTA car parks; the static dataset
  (`car_park`) is HDB-only.** `car_park_availability` intentionally has no foreign key to
  `car_park` — unmatched rows are expected data, not a bug to "fix."
- **Only `lot_type = 'C'` (regular cars) is surfaced by the nearby-search API.** Other lot
  types (`H` heavy vehicle, `Y` motorcycle, etc.) are stored in `car_park_availability`
  but intentionally excluded from search results — see DESIGN.md for the reasoning.
- **Persistence is Spring Data JPA (`CarParkEntity`/`CarParkAvailabilityEntity`), not
  JdbcTemplate** — migrated after the initial build. `schema.sql` is still the source of
  truth for the schema; `spring.jpa.hibernate.ddl-auto: validate` only checks entity
  mappings against it at startup and will refuse to boot on a mismatch (it already caught
  one real one: `gantry_height` was `NUMERIC(5,2)` but mapped as `double`, which Hibernate
  expects as `FLOAT`/`DOUBLE PRECISION` — fixed by relaxing the column to `DOUBLE
  PRECISION`). Both entities use natural, pre-assigned IDs, so `saveAll()` always does a
  `SELECT`-then-`INSERT`/`UPDATE` per row (via JPA `merge()`), not a single-round-trip
  upsert — a deliberate trade-off at this data volume, not an oversight (see DESIGN.md).
  The nearby-search join has no JPA association to traverse (no FK, see above), so it's a
  JPQL query with an explicit `JOIN ... ON` between two otherwise-unrelated entities,
  projected via a constructor expression into `CarParkAvailabilitySnapshot`.
- **The "Parking Finder Internals" Claude Artifact (published earlier) is now stale** —
  it documents the pre-migration JdbcTemplate-based repositories and the `CarPark`/
  `CarParkAvailability` records that no longer exist, and (as of the entities/exceptions
  package move and the `NearbyCarParkQuery` rename above) the pre-reorg package layout
  too. Needs a refresh before relying on it as a reference; not yet done as of this note.
- **IntelliJ may flag `Repository<T, ID>` type arguments with "Non-null type argument is
  expected."** Spring Framework 7 (pulled in by Boot 4.1.1) adopted JSpecify null-safety
  annotations across its APIs; IntelliJ's inspection can't statically prove a plain,
  unannotated project class satisfies the now-annotated non-null bound. This is a
  JSpecify/IDE-tooling concern, not enforced by `javac` — it doesn't block a real build.
  Silence it with a `package-info.java` declaring `@org.springframework.lang.NonNullApi`
  on the affected package if it's worth cleaning up, but don't treat it as a real error.
- **A repository interface's name should reflect what it's for, not just what generic
  base it extends.** `NearbyCarParkQuery` (in `search`) extends Spring Data's bare
  `Repository<>` purely to get a proxy — it manages no entity's lifecycle and returns a
  read-only projection, so it isn't a repository in the domain sense. Watch for this
  pattern elsewhere: extending `Repository<>`/`JpaRepository<>` doesn't automatically
  make "Repository" the right name for the interface.
