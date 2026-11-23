# Configuration Mapping

`src/main/resources/application.yml` is the canonical property contract. Spring Boot resolves the environment substitutions shown here; secrets are never embedded in Java.

| Property | Environment variable | Consumer |
|---|---|---|
| `spring.datasource.url` | `DATABASE_URL` | Spring Boot datasource, Hikari, Flyway, JPA |
| `spring.datasource.username` | `DATABASE_USERNAME` | Spring Boot datasource |
| `spring.datasource.password` | `DATABASE_PASSWORD` | Spring Boot datasource |
| `spring.datasource.hikari.maximum-pool-size` | `DB_POOL_MAX_SIZE` | Spring Boot Hikari binding |
| `spring.datasource.hikari.minimum-idle` | `DB_POOL_MIN_IDLE` | Spring Boot Hikari binding |
| `spring.datasource.hikari.connection-timeout` | `DB_CONNECTION_TIMEOUT_MS` | Spring Boot Hikari binding |
| `server.port` | `PORT` | Spring Boot web server |
| `app.jwt.secret` | `JWT_SECRET` | `AppProperties` → `JwtService` |
| `app.jwt.access-token-minutes` | `JWT_ACCESS_TOKEN_MINUTES` | `AppProperties` → `JwtService` |
| `app.jwt.refresh-token-days` | `JWT_REFRESH_TOKEN_DAYS` | `AppProperties` → `AuthService` |
| `app.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | `AppProperties` → Spring Security CORS |
| `app.openai.api-key` | `OPENAI_API_KEY` | `AppProperties` → `OpenAiClient` |
| `app.openai.model` | `OPENAI_MODEL` | `AppProperties` → `OpenAiClient` |
| `app.openai.timeout-seconds` | `OPENAI_TIMEOUT_SECONDS` | `AppProperties` → `OpenAiClient` |
| `app.recommendations.priority-weight` | `RECOMMENDATION_PRIORITY_WEIGHT` | `AppProperties` → `RecommendationEngine` |
| `app.recommendations.urgency-weight` | `RECOMMENDATION_URGENCY_WEIGHT` | `AppProperties` → `RecommendationEngine` |
| `app.recommendations.deadline-weight` | `RECOMMENDATION_DEADLINE_WEIGHT` | `AppProperties` → `RecommendationEngine` |
| `app.recommendations.goal-relevance-weight` | `RECOMMENDATION_GOAL_RELEVANCE_WEIGHT` | `AppProperties` → `RecommendationEngine` |

Set the environment variables in the same shell/process that launches Maven. PowerShell does not load `.env` files automatically. `.env.example` is documentation only and must not contain real credentials.

The database URL must be a PostgreSQL JDBC URL with `sslmode=require`, and must not contain user information; supply credentials through their separate variables. JWT secret must be base64-encoded and decode to at least 32 bytes. OpenAI API key may be empty; the AI client is not called during startup.
