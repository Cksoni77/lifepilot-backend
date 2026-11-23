# LifePilot Backend API

Base path: `/api`. Protected routes require `Authorization: Bearer <access-token>`. Ownership is always derived from the authenticated JWT, not a client-supplied user ID. JSON errors have `timestamp`, `status`, `error`, `message`, and `path`.

The existing Flutter app expects array responses for tasks, habits, and goals. Those response shapes are preserved. See [BACKEND_SETUP.md](BACKEND_SETUP.md) for Neon/environment configuration.

## Authentication and profile

| Method | Path | Request / result |
|---|---|---|
| POST | `/api/auth/register` | `{name,email,password}`; returns access/refresh tokens and user |
| POST | `/api/auth/login` | `{email,password}`; returns access/refresh tokens and user |
| POST | `/api/auth/refresh` | `{refreshToken}`; rotates the hashed refresh token |
| POST | `/api/auth/logout` | `{refreshToken}`; revokes refresh token |
| GET | `/api/users/me` | Current profile |
| PUT | `/api/users/me` | `{name}` |
| GET / PUT | `/api/users/me/preferences` | Current user's preferences |

Passwords must be 12–72 characters and are BCrypt-hashed. Refresh tokens are opaque random values stored as SHA-256 hashes and pessimistically locked during refresh/logout. Email verification/reset endpoints exist but return 503 because no email-delivery workflow is configured.

## Tasks

| Method | Path | Notes |
|---|---|---|
| GET | `/api/tasks` | Optional `search`, `status` (`all`, `pending`, `completed`, `overdue`, `today`), `sort` (`createdAt`, `priority`, `dueDate`), `category`, `priority`, `completed`, `dueFrom`, `dueTo` |
| GET | `/api/tasks?page=0&size=50` | Optional zero-based in-memory page; size 1–200; array remains compatible and pagination metadata is in `X-Total-Count`, `X-Page`, `X-Page-Size` |
| POST | `/api/tasks` | Creates task; accepts Flutter's `dueAt` / `reminderAt` request field names and optional `goalId`, `clientId`, `completed`, `completedAt` |
| GET / PUT / DELETE | `/api/tasks/{id}` | Owner-scoped operations |
| PATCH | `/api/tasks/{id}/complete` | `{completed: true|false}` |
| GET | `/api/tasks/today` | Pending tasks due today (UTC date) |
| GET | `/api/tasks/overdue` | Pending tasks due before today (UTC date) |
| GET | `/api/tasks/upcoming` | Pending tasks with due dates today or later |
| GET | `/api/tasks/reminders/upcoming` | Optional offset-aware `from` and `to`; defaults to now through seven days |

Task responses use `dueDate` and `reminderDate`, as expected by the current Flutter `Task` model. Categories and priorities are uppercase on input and lowercase on output. Date query filters use UTC calendar dates. Repeated creates with the same authenticated-user `clientId` are safe to retry.

## Habits and goals

- Habits: `GET/POST /api/habits`; `GET/PUT/DELETE /api/habits/{id}`; `POST /api/habits/{id}/complete` with `{date}`; `DELETE /api/habits/{id}/completions?date=YYYY-MM-DD`; `GET /api/habits/{id}/history`; `GET /api/habits/statistics`; `GET /api/habits/{id}/statistics`.
- Global habit statistics return total habits and scheduled/completed counts from the current UTC week.
- Per-habit progress returns completion count and current/longest streak. A streak counts consecutive completion dates; a missed calendar date breaks it. Current streak begins today when completed today, otherwise yesterday. Duplicate same-day completions are prevented by a database unique constraint.
- Goals: `GET/POST /api/goals`; `GET/PUT/DELETE /api/goals/{id}`; `PATCH /api/goals/{id}/progress` with progress in `[0,1]`.

Habit and goal requests/response DTOs retain the Flutter field names, including selected weekdays, completion dates, milestones, and target dates.

## Calendar

- `GET /api/calendar` or `/api/calendar/events` lists current-user calendar events.
- `POST /api/calendar/events`, `PUT /api/calendar/events/{id}`, and `DELETE /api/calendar/events/{id}` manage events.
- `GET /api/calendar/conflicts?startsAt=<offset-date-time>&endsAt=<offset-date-time>&excludeEventId=<uuid>` reports overlapping events without silently changing the schedule.
- `GET /api/calendar/tasks?date=YYYY-MM-DD&timeZone=America/Los_Angeles` retrieves tasks due on a local date.
- `GET /api/calendar/tasks/range?from=YYYY-MM-DD&to=YYYY-MM-DD&timeZone=UTC` groups due tasks by date. Date intervals are inclusive and converted to half-open instants; the maximum range is 366 days. `timeZone` defaults to UTC and must be an IANA zone.

## Dashboard and AI

- `GET /api/dashboard/summary` returns authenticated-user task counts, completion percentage, weekly habit counts/streaks, goal progress, and up to five upcoming tasks.
- `POST /api/ai/chat` accepts `{message, conversationId?}` and returns validated structured response fields and provider availability.
- `GET /api/ai/conversations` lists the current user's conversations; `GET /api/ai/conversations/{id}/messages` returns owner-scoped history.
- `POST /api/ai/daily-plan` returns ranked pending tasks. It does not invent time slots or modify records.

OpenAI configuration is optional and backend-only. Without a key, deterministic responses are used; a live provider request has not been verified. AI suggested actions are proposals and never mutate user data.

## Health and API reference

- `GET /actuator/health` is public and returns only aggregate health information.
- `/v3/api-docs` and `/swagger-ui/index.html` provide generated OpenAPI documentation.
- There is no separate API for notifications: `/api/tasks/reminders/upcoming` reports enabled future reminders; device notification scheduling stays in Flutter.
