# Backend Build and Runtime Status

Status from the configuration-consistency update, run on 2026-11-23.

| Check | Status | Evidence |
|---|---|---|
| `.\mvnw.cmd clean compile` | PASS | `BUILD SUCCESS`; 58 production sources compiled |
| `.\mvnw.cmd test` | PASS | 34 tests; 0 failures, 0 errors, 0 skipped |
| `.\mvnw.cmd clean package` | PASS | `BUILD SUCCESS`; 58 production sources compiled, executable Spring Boot JAR created |
| Configuration binding | PASS | `AppPropertiesTest` binds JWT/CORS/OpenAI/recommendation settings and verifies Hikari pool values |
| Database URL validation | PASS | Validator tests require PostgreSQL JDBC, separate credentials, and `sslmode=require` |
| JWT and recommendation unit checks | PASS | Existing token validation/expiry and configured scoring tests pass |
| Application startup | BLOCKED | `.\mvnw.cmd spring-boot:run` reports `Required environment variable DATABASE_URL is not set (required by spring.datasource.url)` |
| Neon connectivity | NOT TESTED | No current database credentials are set; no connection was attempted |
| Hikari initialization against Neon | NOT TESTED | Requires a valid database configuration |
| Flyway migration execution | NOT TESTED | Requires a valid database configuration |
| Hibernate schema validation | NOT TESTED | Requires a valid database configuration |
| Actuator health HTTP response | NOT TESTED | Requires the application to start |
| OpenAI live request | NOT TESTED | Provider key is optional and not configured |

The startup failure is intentional and safe: the application no longer falls back to an embedded database URL or uses checked-in database credentials. Set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` in the same shell that runs Maven, along with `JWT_SECRET` and `CORS_ALLOWED_ORIGINS`. See [BACKEND_SETUP.md](BACKEND_SETUP.md).

## Required manual verification

After adding fresh Neon development credentials in the process environment:

1. Run `.\mvnw.cmd spring-boot:run`.
2. Verify Flyway and Hibernate schema validation complete against the intended development database.
3. Request `GET /actuator/health` and exercise auth plus a protected owner-scoped endpoint.

Never use a production database for destructive/integration testing. Previously exposed database and JWT credentials must be rotated.
