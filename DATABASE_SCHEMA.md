# LifePilot Database Schema

Flyway is the only schema manager. Existing migration history is preserved:

- `V1__initial_schema.sql` creates the existing domain schema.
- `V2__client_import_ids.sql` adds idempotent client import identifiers to tasks, habits, and goals.

Hibernate is configured with `ddl-auto: validate`, `open-in-view: false`, and UTC JDBC time zone. Database credentials are required environment variables; no local PostgreSQL fallback is configured.

| Table | Data and ownership |
|---|---|
| `users` | UUID user, unique normalized email, BCrypt password hash, timestamps |
| `refresh_tokens` | User-owned token hash, expiry and revocation timestamps |
| `email_verification_tokens`, `password_reset_tokens` | Reserved token storage; email workflows are not implemented |
| `user_preferences` | One timezone/theme/notification preference record per user |
| `tasks` | User-owned task, status, category, priority, due/reminder timestamps, optional goal |
| `habits` | User-owned frequency, category, duration target, and start date |
| `habit_weekdays` | Weekday schedule, keyed by habit and weekday |
| `habit_completions` | Date-only completion, unique per habit/date |
| `goals` | User-owned goal progress constrained to `[0,1]`, optional target date |
| `goal_milestones` | Goal-owned milestone titles |
| `calendar_events` | User-owned timezone-aware intervals with positive-duration constraint |
| `ai_conversations`, `ai_messages` | User-owned conversations and messages |
| `ai_recommendations` | User-owned persisted recommendation data table |

Foreign keys cascade for owned records where appropriate. Task-to-goal references are optional and use `ON DELETE SET NULL`; account deletion cascades user-owned records. The migrations define indexes for task owner/due/completion, goals, habits, refresh tokens, calendar ranges, AI conversations/messages, and recommendation history.

Timestamps use PostgreSQL `TIMESTAMPTZ`; date-only goal targets, habit starts, and habit completions use `DATE`. User-selected calendar date ranges are translated to half-open instants using the requested IANA time zone; UTC is the default.

No new migration was needed for the implemented query/API features. Do not edit V1 or V2 after application; create a new numbered migration when the entity model genuinely changes.
