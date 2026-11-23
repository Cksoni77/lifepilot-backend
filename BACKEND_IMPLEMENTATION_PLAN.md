# LifePilot Backend Implementation Plan

## Inspection findings

Reviewed the Maven configuration, Spring configuration, Java feature packages, migrations, tests, API documentation, and Flutter `Task`, `Habit`, and `Goal` models and their API services.

### Already implemented

- Java 17 / Spring Boot 3.4.5 Maven application with feature-based layers, DTOs, JPA repositories/entities, validation, and centralized exception handling.
- BCrypt registration/login, signed expiring JWT access tokens, hashed rotating refresh tokens, logout revocation, stateless security, CORS configuration, and current-user-derived ownership checks.
- Task CRUD/completion and search/status/sort; habit CRUD, completion history, duplicate protection, and weekly counts; goal CRUD and progress; preferences; calendar-event CRUD/conflict detection.
- Optional OpenAI-backed chat with a deterministic fallback and no provider call during startup; configurable recommendation weights.
- V1/V2 migrations for the current model, with UUIDs, foreign keys, checks, unique constraints, indexes, and timestamps.
- Flutter API compatibility: task requests use `dueAt`/`reminderAt` and responses use `dueDate`/`reminderDate`; Flutter consumes array responses for tasks/habits/goals and the existing completion/chat routes.
- Existing unit tests for authentication/JWT/password hashing, owner isolation, calendar conflicts, and recommendation ranking.

### Partially implemented / gaps

- **Critical configuration risk (resolved):** the inspected `application.yml` contained literal database credentials. Datasource values now use required environment variables; rotate any values exposed in prior configuration.
- Maven wrapper is pinned to Maven 3.9.9.
- Tasks lack category/priority/date filtering and page/size options. Do not change the default array response consumed by Flutter.
- Habit and goal services lack owner-scoped single-record retrieval. Habit streak values are computed only in the Flutter model; the backend has no per-habit streak response.
- Calendar supports events, but not task-by-date/date-range lookup. No backend upcoming-reminder query exists; local device notifications remain a Flutter responsibility.
- No authenticated dashboard summary, Actuator health endpoint, or generated OpenAPI/Swagger documentation.
- API error bodies omit request path, and security-filter 401 responses are not in the same error format.
- Existing unit tests do not sufficiently cover refresh-token rotation/logout, API filters, streak rules, or dashboard calculations. There is no isolated database integration-test setup.
- Email verification and password reset endpoints are explicit 503 placeholders. The Flutter profile preferences service remains local-only. These are known integration gaps, not implemented flows.

### Persistence and security notes

- Migrations match the inspected entities and existing API needs; no schema change is justified by this scope. Preserve V1/V2 and add a migration only if an actual mapped field/table is introduced.
- Data ownership consistently comes from the authenticated JWT principal. Keep Flutter request/response field names and do not accept client-supplied owner IDs.
- Database and JWT secrets must not be logged, documented, committed, or used from the checked-in literal configuration. Remote database behavior cannot be validated without fresh environment credentials.
- Habit streak semantics will match the current Flutter model: a streak counts consecutive calendar dates with a completion; current streak starts today if completed today, otherwise yesterday; missed dates break the streak. Completion records are unique per habit/date and stored as `DATE`.
- Calendar date queries will use an explicit IANA time-zone parameter (default UTC) to convert local dates to half-open UTC timestamp ranges.

## Implementation sequence

1. Remove literal secrets and require Neon/JWT environment variables; keep 15/30-day token defaults and optional OpenAI settings.
2. Add missing owner-scoped detail, query, dashboard, calendar-task, and upcoming-reminder APIs without breaking Flutter response contracts.
3. Standardize API/security errors; add health and OpenAPI support.
4. Add focused unit tests for new business rules and authentication rotation.
5. Update setup, API, and database documentation; keep migration history unchanged unless entity reconciliation proves a schema addition is needed.
6. Run compile, tests, package, and startup attempts. Mark Neon/Flyway/Hibernate and live provider checks BLOCKED/NOT TESTED unless their external dependencies are explicitly configured.

## Implementation and verification outcome

- Typed `AppProperties` now supplies JWT, CORS, OpenAI, and recommendation consumers. Spring Boot's standard datasource/Hikari binding remains in use.
- Datasource startup validation now reads Spring's resolved datasource properties and reports the missing corresponding environment variable without exposing values.
- `.env.example`, `CONFIGURATION_AUDIT.md`, and `CONFIGURATION_MAPPING.md` document the placeholder-only environment contract and binding paths.
- `.\mvnw.cmd clean compile`, `.\mvnw.cmd test` (32 tests), and `.\mvnw.cmd clean package` passed.
- Application startup is BLOCKED because `DATABASE_URL` is absent from the Maven process environment. Neon, Flyway, Hibernate validation, Actuator HTTP, and live AI behavior remain unverified. See [BACKEND_BUILD_STATUS.md](BACKEND_BUILD_STATUS.md).
