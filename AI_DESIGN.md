# AI design

`POST /api/ai/chat` derives the account UUID from the JWT, loads at most 100 of that user's recent tasks plus that user's habits and goals, and sends bounded saved context to the backend-configured local Ollama service. Ollama uses the configurable model and timeouts and is never called directly by Flutter.

`POST /api/ai/quick-add` sends only the authenticated user's text, configured timezone, current local date, and selected profiles to Ollama. The response is constrained to a JSON Schema and validated against existing task categories, profile/activity combinations, lengths, and numeric bounds. The endpoint does not load or write task records. It returns stable per-request client IDs for the Flutter confirmation flow to use with the existing idempotent task API.

The output is parsed into a structured response. Task references not found among the authenticated user's loaded tasks are removed. A non-empty proposed-action list makes `requiresConfirmation` true. No suggested action is executed.

When Ollama is disabled, or a chat provider/JSON response fails, deterministic replies can answer saved tasks due today, overdue tasks, and task-priority questions. Quick Add returns a safe service error instead of silently fabricating or persisting suggestions when Ollama is unavailable or returns invalid data.

## Initial priority heuristic

`score = 0.30 × priority + 0.30 × urgency + 0.20 × deadline proximity + 0.20 × goal relevance`. Inputs are on a 0–100 scale; weights are environment-configurable and must sum to 1:

- Priority: high 100, medium 60, low 30.
- Urgency: overdue 100, due today 90, within two days 75, within seven days 55, within 30 days 35, later 15, no deadline 0.
- Deadline proximity: due/overdue 100; otherwise `100 / (daysUntilDue + 1)`; no deadline 0.
- Goal relevance: linked task 100, unlinked task 0.

This is an initial heuristic, not a validated productivity model. Task duration is included in explanations and context, but no calendar availability is available for slot allocation. Daily planning therefore ranks tasks and asks questions instead of inventing a schedule.

Chat conversation history is persisted under the authenticated account. Quick Add text and suggestions are not persisted by the AI endpoint. The Ollama base URL is configurable; deployments should keep the service on a private network and obtain user consent consistent with their privacy policy.
