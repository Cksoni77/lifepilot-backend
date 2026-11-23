# LifePilot Backend Setup

## Requirements

- Java 17
- Maven Wrapper (included; downloads Maven 3.9.9)
- A Neon PostgreSQL database

The backend has no local database fallback. Spring Boot does not load dotenv files; set the environment in the shell that runs Maven or use a deployment secret manager. `.env.example` contains placeholders only. Startup reports the missing required variable without printing credential values.

## Configure Neon and JWT (PowerShell)

Use the connection details from the Neon console. Set the JDBC URL, username, and password separately. Keep TLS enabled with `sslmode=require`.

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://<NEON_HOST>/<DATABASE>?sslmode=require'
$env:DATABASE_USERNAME = '<NEON_USERNAME>'
$env:DATABASE_PASSWORD = '<NEON_PASSWORD>'
$env:JWT_SECRET = python -c "import secrets,base64;print(base64.b64encode(secrets.token_bytes(32)).decode())"
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:3000,http://localhost:8081'
$env:JWT_ACCESS_TOKEN_MINUTES = '15'
$env:JWT_REFRESH_TOKEN_DAYS = '30'
```

These are session-scoped PowerShell variables and must be set in the same terminal that runs `.\mvnw.cmd spring-boot:run`. If startup reports `DATABASE_URL is not set`, the application correctly has no database URL in that process; set the variables above using current Neon credentials, then rerun the command. Do not copy previously committed connection credentials back into the YAML file.

Never commit actual credentials or include them in logs, issue reports, or documentation. Any database credentials previously exposed in `application.yml` must be rotated before use; they are no longer read by the application.

## Optional and defaulted configuration

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `OPENAI_API_KEY` | empty | Optional backend-only provider key |
| `OPENAI_MODEL` | `gpt-4.1-mini` | Provider model |
| `OPENAI_TIMEOUT_SECONDS` | `20` | Provider timeout |
| `DB_POOL_MAX_SIZE` | `10` | Hikari maximum connections |
| `DB_POOL_MIN_IDLE` | `2` | Hikari minimum idle connections |
| `DB_CONNECTION_TIMEOUT_MS` | `30000` | Connection acquisition timeout |

`CORS_ALLOWED_ORIGINS` is required. Set it to comma-separated browser origins, such as `http://localhost:3000,http://localhost:8081`.

OpenAI configuration is optional and is never called during startup. Missing provider credentials do not disable non-AI endpoints.

## Run and verify

From the backend project root:

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd test
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

The wrapper is pinned to Maven 3.9.9. Alternatively, installed Maven 3.9+ can run the same goals.

Flyway applies versioned files under `src/main/resources/db/migration`. Hibernate validates the migrated schema (`ddl-auto: validate`) and does not create or update it. Preserve applied migrations and add a new migration for a schema change.

## Endpoints and operational notes

- Health: `GET /actuator/health` (only health is exposed; details are hidden).
- OpenAPI JSON: `/v3/api-docs`; Swagger UI: `/swagger-ui/index.html`.
- Authenticated APIs require `Authorization: Bearer <access-token>`.
- Use HTTPS, restricted CORS, fresh per-environment secrets, database backups, and secret-manager injection in deployed environments.
- No isolated database integration profile is configured. Do not run destructive tests against production Neon.

See [API_DOCUMENTATION.md](API_DOCUMENTATION.md), [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md), and [BACKEND_BUILD_STATUS.md](BACKEND_BUILD_STATUS.md).
