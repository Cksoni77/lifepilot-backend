# LifePilot

LifePilot is a Flutter personal-management app with a Spring Boot REST backend. The backend owns account authentication and task, habit, and goal records in PostgreSQL. Flutter stores access and refresh tokens with `flutter_secure_storage`; preferences and pre-existing local data remain on the device.

## Run locally (Windows / Android emulator)

1. Start PostgreSQL and configure the backend environment. See [SETUP_GUIDE.md](SETUP_GUIDE.md).
2. From this backend directory, start Spring Boot:

   ```powershell
   $env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/lifepilot'
   $env:DATABASE_USERNAME = 'lifepilot'
   $env:DATABASE_PASSWORD = 'replace-with-a-local-password'
   $env:JWT_SECRET = python -c "import secrets,base64;print(base64.b64encode(secrets.token_bytes(32)).decode())"
   mvn spring-boot:run
   ```

3. In a second terminal, enter the sibling Flutter directory and run the app:

   ```powershell
   Set-Location ..\lifepilot
   flutter pub get
   flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
   ```

   `10.0.2.2` is the Android emulator's route to the host computer. Use your computer's LAN IP for a physical device, `http://localhost:8080` for an iOS simulator or local web development, and HTTPS for production.

The mobile app requires the backend for sign-in and task/habit/goal operations. AI chat uses deterministic saved-data answers when `OPENAI_API_KEY` is absent; natural-language AI requires a backend key. No API key belongs in Flutter.

## Project map

- `..\lifepilot\` — sibling Flutter application and feature modules
- `.` — Java 17, Spring Boot 3.4, Maven REST API
- `src\main\resources\db\migration\` — Flyway PostgreSQL migrations

## Documentation

- [Migration analysis](../MIGRATION_ANALYSIS.md)
- [Migration status](../MIGRATION_STATUS.md)
- [Implementation status and known gaps](IMPLEMENTATION_STATUS.md)
- [Setup guide](SETUP_GUIDE.md)
- [Architecture](ARCHITECTURE.md)
- [Database design](DATABASE_DESIGN.md)
- [API documentation](API_DOCUMENTATION.md)
- [AI design](AI_DESIGN.md)
- [Security](SECURITY.md)
- [Testing](TESTING.md)

## Checks

```powershell
mvn test
Set-Location ..\lifepilot
flutter analyze
flutter test
```

PostgreSQL is required for backend startup and database integration tests. See [TESTING.md](TESTING.md) for what has and has not been run in this environment.
