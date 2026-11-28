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
| `app.ollama.enabled` | `OLLAMA_ENABLED` | `AppProperties` → `OllamaClient` |
| `app.ollama.base-url` | `OLLAMA_BASE_URL` | `AppProperties` → `OllamaClient` |
| `app.ollama.model` | `OLLAMA_MODEL` | `AppProperties` → `OllamaClient` |
| `app.ollama.connect-timeout-seconds` | `OLLAMA_CONNECT_TIMEOUT_SECONDS` | `AppProperties` → `OllamaClient` |
| `app.ollama.response-timeout-seconds` | `OLLAMA_RESPONSE_TIMEOUT_SECONDS` | `AppProperties` → `OllamaClient` |
| `app.recommendations.priority-weight` | `RECOMMENDATION_PRIORITY_WEIGHT` | `AppProperties` → `RecommendationEngine` |
| `app.recommendations.urgency-weight` | `RECOMMENDATION_URGENCY_WEIGHT` | `AppProperties` → `RecommendationEngine` |
| `app.recommendations.deadline-weight` | `RECOMMENDATION_DEADLINE_WEIGHT` | `AppProperties` → `RecommendationEngine` |
| `app.recommendations.goal-relevance-weight` | `RECOMMENDATION_GOAL_RELEVANCE_WEIGHT` | `AppProperties` → `RecommendationEngine` |

Set the environment variables in the same shell/process that launches Maven. PowerShell does not load `.env` files automatically. `.env.example` is documentation only and must not contain real credentials.

The database URL must be a PostgreSQL JDBC URL with `sslmode=require`, and must not contain user information; supply credentials through their separate variables. JWT secret must be base64-encoded and decode to at least 32 bytes. Ollama is contacted only when an authenticated AI endpoint is called.
