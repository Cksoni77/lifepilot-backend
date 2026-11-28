# Architecture

```mermaid
flowchart LR
  UI[Flutter screens and feature modules]
  API[Flutter ApiClient]
  AUTH[Spring Security and JWT]
  SERVICES[Spring services: auth, tasks, habits, goals, AI]
  DB[(PostgreSQL)]
  OLLAMA[Private local Ollama]

  UI --> API
  API -->|HTTPS / JSON, Bearer access token| AUTH
  AUTH --> SERVICES
  SERVICES --> DB
  SERVICES -->|Bounded prompt/context| OLLAMA
```

Flutter uses a centralized `ApiClient` with a compile-time `API_BASE_URL`, bearer authentication, refresh-token rotation, typed network errors, and a 20-second request timeout. `AuthService` stores both tokens through `flutter_secure_storage`. Task, habit, and goal services call REST endpoints; user preferences remain local in the current UI.

The backend uses Spring Boot 3.4 / Java 17, stateless Spring Security, BCrypt, JPA repositories, Flyway migrations, and DTO validation. Every domain query is scoped using the UUID extracted from the validated JWT security context. A request-supplied user ID is never used for authorization. Ollama is called only from the backend using the configured private base URL and model.

```mermaid
sequenceDiagram
  participant U as Flutter user
  participant F as Flutter ApiClient
  participant S as Spring Security/API
  participant D as PostgreSQL
  participant A as Ollama (local)
  U->>F: Ask question
  F->>S: POST /api/ai/chat + access JWT
  S->>D: Load authenticated user's limited context
  alt Ollama enabled
    S->>A: Structured local model request
    A-->>S: Structured JSON response
  else Provider absent/unavailable
    S->>S: Deterministic saved-task response
  end
  S-->>F: Validated response, no executed actions
  F-->>U: Display answer
```

Quick Add follows a separate confirmation path: the AI endpoint returns validated suggestions without persisting them, and Flutter creates approved items through the existing task API. No AI-proposed action is directly applied to persistent data. Daily-plan output does not assign times while availability and fixed commitments are unavailable.

## Deliberate limitations

See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md): email workflows, calendar CRUD/conflict detection, offline sync, conversation persistence, rate limiting, and local-data import/export remain unfinished.
