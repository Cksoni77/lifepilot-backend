# Testing

## Commands

```powershell
mvn test
Set-Location ..\lifepilot
flutter analyze
flutter test
```

Backend unit tests cover recommendation ordering/weights, JWT subject validation, BCrypt hashing, task-owner scoping, and calendar conflict rules. Flutter tests use a mocked HTTP client; normal tests do not call OpenAI.

## Validation record

- Before implementation: `flutter analyze` reported no issues and `flutter test` passed all 47 tests.
- During implementation: Flutter analysis detected a misplaced helper and an unused test import; both were fixed.
- Backend: `mvn test` passed all 11 tests, including authenticated task ownership and repeated-client-ID behavior.
- Flutter: `flutter analyze` passed and `flutter test` passed all 51 tests, including retry-safe legacy import and preservation of local source data.
- PostgreSQL integration/server startup, Flutter device launch, and Flutter-to-backend end-to-end flows have not been verified. No PostgreSQL server was available and Docker Desktop was not running during the original inspection.
