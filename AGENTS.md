# recommendation-service

Builds the personal list of materials. The vendor LLM ranks and explains; this service supplies the context, checks the answer and logs what was shown.

**Status:** an empty Spring Boot application. The team builds it after the frontend; until then, change it only when asked.

## Project

Recommendation System is an entry for Hack&Change 2026, Alfa-Bank case «Альфа-Курс: знания в нужный момент», track 1: personal recommendations of educational materials for entrepreneurs. A vendor LLM ranks and explains materials; the services around it supply context, check its answers and record what users do.

| Repository | Role |
|---|---|
| `api-gateway` | Single entry point. Checks access tokens, routes `/api/v1/**`, sets `X-User-Id`. |
| `auth-service` | Registration, login, access and refresh JWT, logout denylist. |
| `profile-service` | Business profile of the user (onboarding answers). |
| `catalog-service` | Catalog of materials from the case data, with Russian full-text search. |
| `metric-service` | Raw user events and the metrics computed from them. |
| `recommendation-service` | Builds recommendations with the LLM. |
| `frontend` | Angular web app. |
| `Documentation` | Architecture docs. Only developers commit there; agents never do. |

All repositories live in the GitHub organization `Hack-Challenges-recommendation-system` and are **public**.

## Rules for every change

These come from the case. Breaking one can disqualify the submission.

1. **The case data never goes into git or a Docker image**: not the dump, not a copy, not an extract. Services read it from a path mounted at runtime; `/data/` is in `.gitignore`. Synthetic test data written by the team may be committed.
2. **No keys, tokens or passwords in git.** Read them from environment variables; `env.example` files list names only.
3. **External APIs, such as the LLM, get only the fragments they need.** No user ids, names, emails or other personal data, and no paid or closed materials.
4. **Paid materials:** show only a short benefit and the provided link, never their content.
5. **Everything must start with one `docker compose up` from the README.** A submission that doesn't start scores 0. Don't add infrastructure or a required external service without agreeing on it first.
6. **Simple UI, no intrusive notifications.**

## Commits

Every commit follows [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/), in the style the history already uses:

    <type>(<scope>): <what was changed>

- **Type:** `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `style` or `revert`.
- **Scope:** optional; the area that changed, e.g. `(jwt)`, `(kafka)`, `(migrations)`, `(controllers)`, `(CI/CD)`.
- **Description:** English, past tense, e.g. `fix(cookie): logout cookie name was fixed`.
- **Breaking change:** `!` after the type or scope, plus a `BREAKING CHANGE:` footer saying what callers must change.
- One logical change per commit. Commit to `develop`; `main` receives merges from `develop`.

## Code conventions

- Java 25, Spring Boot 4.1.2, Maven. Base package `team.nofantasy.recommendationsystem.<service>`, entry point `Main`.
- Three layers: `controller` → `service` → `repository`. Controllers never touch repositories or entities.
- Two DTO layers, both Java records:
  - `dto/controller/<feature>/*ControllerDto`: HTTP requests and responses, with Jakarta validation.
  - `dto/service/<feature>/*ServiceDto`: between controllers and services. Kafka messages live in `dto/service/kafka`, import rows in `dto/service/importer`.
- Mapping goes through MapStruct interfaces in `mapper/`. Unmapped source or target properties fail the build, so map every field or ignore it explicitly. Keep methods in pairs: controller-to-service first, then service-to-controller.
- Entities in `entity/` use Lombok: `@Getter`, `@Setter`, `@Builder` and a protected no-args constructor. Enums of an entity are nested in it.
- Domain exceptions live in `exception/`; `controller/advice/GlobalExceptionHandler` turns them into HTTP statuses.
- Schema changes go only into new Flyway files in `src/main/resources/database/migrations` (`V<n>__<description>.sql`). Never edit a migration that a shared database already ran. Hibernate runs with `ddl-auto: validate`.
- Endpoints sit under `/api/v1` (`spring.mvc.servlet.path`). Each service owns distinct top-level paths, so the gateway can route by prefix.
- The gateway puts the signed-in user's id into `X-User-Id`. Read it with `@RequestHeader("X-User-Id") UUID userId`, never from the body.
- Log with the SLF4J fluent API: `log.atInfo().setMessage(...).addKeyValue(...).log()`. Files go to `/logs/<service>.log` as ECS JSON.
- Configuration comes from environment variables, with local defaults in `application.yaml`.

## Build and test

- `mvn -B -ntp verify` runs all tests. Integration tests use Testcontainers, so Docker must be running; without Docker they are skipped.
- `mvn -B -ntp -DskipTests package` builds `target/app.jar`, which the `Dockerfile` copies.
- `docker compose up -d` starts the local infrastructure of this service (`docker-compose.yaml`).
- In MockMvc tests, request the full path and set the servlet path: `get("/api/v1/...").servletPath("/api/v1")`.
- CI: `.github/workflows/ci-cd.yml` tests every push and pull request, pushes the image to Docker Hub from `main`, and deploys on a manual run. Its header lists the secrets and variables it needs.

## Planned stack

- Spring AI 2.0.x with `org.springframework.ai:spring-ai-starter-model-anthropic`, which runs on the official Anthropic Java SDK. Spring AI 2.0.2 is built against Spring Boot 4.1.2, which is why every service is on 4.1.2.
- Model `claude-opus-5-5`; the key comes from `ANTHROPIC_API_KEY`. Never log or commit it.

## Planned request pipeline

1. **Context:** the profile from profile-service (`GET /profiles/me` with the caller's `X-User-Id`), recent events from metric-service (`GET /events/me`), the catalog from catalog-service (cache it in memory).
2. **Candidates:** 30–50 materials. Drop completed and unavailable ones, match the goal text with `GET /materials/search`, add `GET /metrics/materials/popular` and `GET /metrics/materials/{id}/next`. Sorted by text match, this list is the topic-match baseline the case asks to compare against.
3. **LLM:** send the candidates and the business fields of the profile. Get back JSON with ordered ids, a reason for each, a next step, or a clarifying question when nothing fits.
4. **Validation:** ids must be among the candidates, links come from the catalog, and paid materials keep only their benefit and provided link.
5. **Response and logging:** assign the A/B variant by hashing the user id, and send `IMPRESSION` events with `recommendationId`, `variant` and `position` to metric-service. Cache per profile and history, and prepare the demo personas in advance.

## What the LLM may receive

- **Allowed:** public fields of the candidates, the business fields of the profile, the user's own history as material ids and titles, and aggregates such as "popular at your stage".
- **Never:** user ids, nicknames, emails or other personal data, the raw case data or other users' events, and the content of paid materials.

## Spring AI with `claude-opus-5-5`

- Set `spring.ai.anthropic.chat.model` explicitly; the documented default is an older model.
- Never set `temperature`, `top_p` or `top_k`: the model rejects them with HTTP 400.
- Never call `thinkingDisabled()` or `thinkingEnabled(budget)`: also HTTP 400. For latency, set effort `LOW` through `outputConfig`.
- Use the native JSON-schema output, and still validate the answer.
- `max-tokens` defaults to 4096; raise it if answers stop with `max_tokens`.
- Answers aren't deterministic. For the comparison with the baseline, store the outputs and run each test profile 3 times.
- If the LLM call fails (no key, timeout, error, invalid JSON), answer with the baseline and `engine: baseline`, never with a 500.
