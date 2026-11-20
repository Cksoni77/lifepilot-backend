# LifePilot Backend

LifePilot is a Java 17 / Spring Boot REST backend for the Flutter personal-management application. It uses Spring MVC, Spring Security with JWT, Spring Data JPA/Hibernate, Flyway, and remote Neon PostgreSQL.

## Quick start

1. Install Java 17 and Maven 3.9 or later.
2. Create a Neon PostgreSQL project and configure `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` in your PowerShell session. The JDBC URL must use `jdbc:postgresql://HOST/DATABASE?sslmode=require`.
3. Configure `JWT_SECRET` as a base64-encoded random key of at least 32 bytes. `JWT_ACCESS_TOKEN_MINUTES` and `JWT_REFRESH_TOKEN_DAYS` default to 15 and 30.
4. From the backend root, run:

   ```powershell
   mvn clean compile
   mvn test
   mvn clean package
   mvn spring-boot:run
   ```

Full environment-variable and Neon setup instructions are in [BACKEND_SETUP.md](BACKEND_SETUP.md). `.env.example` contains placeholders only; Spring Boot does not load dotenv files automatically. Do not use a local database fallback.

## Architecture

Feature packages under `src/main/java/com/lifepilot` separate controllers, DTOs, services, repositories, and entities. User-owned data is resolved from the authenticated JWT identity. Flyway migrations live in `src/main/resources/db/migration`; Hibernate uses `ddl-auto: validate`.

## API and project docs

- [API endpoints](API_DOCUMENTATION.md)
- [Build audit](BACKEND_BUILD_AUDIT.md)
- [Latest build status](BACKEND_BUILD_STATUS.md)
- [Security](SECURITY.md)
- [Architecture](ARCHITECTURE.md)
- [Database design](DATABASE_DESIGN.md)
- [AI design](AI_DESIGN.md)

AI provider access is optional and configured only on the backend. Email verification and password reset are not available until an email provider is implemented.
