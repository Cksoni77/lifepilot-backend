# Configuration Audit

## Findings

- `LifePilotApplication` inspected `System.getenv()` directly for `DATABASE_URL`, although Spring reads datasource settings through `spring.datasource.*`. This created an error path separate from Spring property resolution.
- The checked-in `application.yml` had a literal Neon JDBC URL, username, and password while startup validation required environment variables. This violated the required configuration contract and exposed credentials in source.
- Scattered `@Value` expressions used the correct `app.*` keys, but did not provide typed, centralized validation. Recommendation weights had duplicate Java fallback defaults.
- CORS had an implicit localhost fallback despite the required environment mapping. It now uses the configured variable and supports comma-delimited origins through typed list binding.
- Hikari settings, Actuator exposure, JPA validation, Flyway, and optional AI configuration were already present in YAML and remain Spring Boot-managed.
- Recommendation defaults are declared in YAML. The scoring implementation consumes those configured weights and rejects out-of-range values or totals that do not equal one.

## Changes

- Mapped the datasource URL, username, and password to `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`; removed literal credentials.
- Added `AppProperties` for JWT, CORS, OpenAI, and recommendation groups with validation and registered it via `@ConfigurationPropertiesScan`.
- Updated JWT, auth, CORS, AI, and recommendation consumers to use the typed configuration object.
- Added early database URL/credentials validation against Spring's resolved datasource properties. Missing values produce a safe message naming the required environment variable; JDBC URL credentials and missing TLS are rejected.
- Added `.env.example` containing placeholders/defaults only.
- Hikari and Flyway remain bound to Spring Boot's normal datasource configuration; no second datasource configuration was added.

## Validation status

- Configuration property and Hikari binding tests: covered by `AppPropertiesTest`.
- JWT configured-key signing, verification, expiration, and invalid-token behavior: covered by `JwtServiceTest`.
- Recommendation weight behavior and rejection of invalid totals: covered by `RecommendationEngineTest`.
- Missing/unsafe datasource configuration: covered by `DatabaseConfigurationValidatorTest`.
- Live Neon connection, migrations, Hibernate validation, and HTTP health response require externally supplied credentials and are not inferred from unit tests.
