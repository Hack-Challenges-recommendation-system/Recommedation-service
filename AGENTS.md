# recommendation-service

Builds the personal list of materials. The vendor LLM chooses and explains; this service supplies the context, checks the answer and logs what was shown.

**Read the shared guide first:** [AGENTS.md in Documentation](https://github.com/Hack-Challenges-recommendation-system/Documentation/blob/develop/AGENTS.md) covers the project, its architecture, the rules from the case (the case data and keys never go into git; this repository is public), the commit conventions and the conventions of the Java services. Locally it is `AGENTS.md` in your clone of the Documentation repository. This file adds only what is specific to recommendation-service.

## Stack

- Spring AI 2.0.2 with `org.springframework.ai:spring-ai-starter-model-anthropic`, which runs on the official Anthropic Java SDK. Spring AI 2.0.2 is built against Spring Boot 4.1.2, which is why every service is on 4.1.2.
- Model `claude-sonnet-5-5` at effort `medium`, the team's choice; `ANTHROPIC_MODEL` and `ANTHROPIC_EFFORT` change them. The key comes from `ANTHROPIC_API_KEY`. Never log or commit it.
- `RestClient` calls to profile-, catalog- and metric-service live in `client/`, next to the usual layers. Caffeine holds the cache; WireMock stands in for the other services in tests.

## How a list is built

`GET /recommendations/me` (signed in) answers `{recommendationId, variant, engine, clarifyingQuestion, items: [{material, reason, nextStep, position}]}`, the shape in frontend/AGENTS.md.

1. **Context:** the profile from profile-service (`GET /profile/me` with the caller's `X-User-Id`) and the newest 100 events from metric-service (`GET /events/me`). Only `START`, `COMPLETE`, `LIKE` and `DISLIKE` count as history. A missing profile or history doesn't fail the request.
2. **Candidates** (`CandidateService`), at most 40: catalog search on the goal, or on the sphere when the goal is empty; materials often taken after the user's 3 most recent ones (`GET /metrics/materials/{id}/next`); popular materials (`GET /metrics/materials/popular`); then materials for the user's stage and preferred formats. Completed and disliked materials are left out. `CatalogService` keeps the catalog in memory for 10 minutes.
3. **Baseline order:** topic match with the goal, then "taken after", stage, preferred format and popularity. The first 6 candidates in this order are the topic-match baseline the case compares against.
4. **LLM** (`LlmRankingService`): the candidates with their signals, the business fields of the profile and the user's own history go to the model as JSON. A JSON schema constrains the answer: ordered ids with a reason and a next step each, and a clarifying question or an empty string.
5. **Validation:** ids must be candidates and count once; at most 6 items. The material always comes from the catalog, so links and paid content are what catalog-service gives. A blank reason or next step is written from the signals (`Explanations`).
6. **Fallback:** without a key, or when the call fails, times out, is refused or doesn't match the schema, the answer is the baseline with `engine: baseline`, never a 500. Only a catalog outage with no cached catalog gives 503, and the frontend then shows catalog search.
7. **Cache:** per user, variant, profile and history, 30 minutes for an LLM list and 2 minutes for a baseline list, so a failing LLM is retried soon. Every response gets a new `recommendationId`. Before a demo, open the recommendations of the demo personas once so they are cached.

## A/B test

- `ExperimentService` puts a user into `llm` or `baseline` by hashing the user id. `AB_LLM_SHARE` (default 100) is the percent in `llm`: set 50 for the experiment, keep 100 for demos.
- `variant` is the assignment and `engine` is what built the list: an `llm` user whose call failed gets `variant: llm` and `engine: baseline`.
- The frontend logs `IMPRESSION` and `CLICK` with `recommendationId`, `variant` and `position`. This service sends no events, so nothing is counted twice; metric-service reports per variant at `GET /metrics/experiment`.
- Every served list is logged as `Recommendations are served`, with the user, the recommendation id, variant, engine, material ids, cache hit and latency.

## What the LLM may receive

- **Allowed:** public fields of the candidates, the business fields of the profile, the user's own history as material ids and titles, and aggregates such as how many users took a material.
- **Never:** user ids, nicknames, emails or other personal data, the raw case data or other users' events, and the content of paid materials.
- `ProfileServiceDto` doesn't even read the user id and nickname. `PersonalDataMasker` replaces emails and phone numbers in the goal and sphere with `[email]` and `[phone]`.

## Spring AI with Claude Sonnet 5.5

- Set `spring.ai.anthropic.chat.model` explicitly: the Spring AI default is `claude-haiku-4-5`.
- Never set `temperature`, `top_p` or `top_k`, and never call `thinkingDisabled()` or `thinkingEnabled(budget)`: the model answers HTTP 400. Thinking stays adaptive.
- Effort goes through `AnthropicChatOptions.builder().effort(...)` and the schema through `.outputSchema(...)`; both end up in one `output_config`.
- Spring AI 2.0.2 can't send the server-side `fallbacks` parameter, so a refusal ends in the baseline.
- `max-tokens` is 8000 and the timeout 45 s with 1 retry; the gateway gives recommendations 120 s. A live call with 8 candidates took about 6 s.
- Answers aren't deterministic. For the comparison with the baseline, store the outputs and run each test profile 3 times.

## Configuration

| Variable | Default |
|---|---|
| `ANTHROPIC_API_KEY` | empty: baseline only |
| `ANTHROPIC_MODEL`, `ANTHROPIC_EFFORT` | `claude-sonnet-5-5`, `medium` |
| `ANTHROPIC_TIMEOUT` | `45s` |
| `PROFILE_SERVICE_URL`, `CATALOG_SERVICE_URL`, `METRIC_SERVICE_URL` | `http://<service>:8080`, the compose names |
| `RECOMMENDATION_ITEMS` | `6` |
| `AB_LLM_SHARE` | `100` |

A local run reads `.env` from the working directory through `spring.config.import`; start from `.env.example`. `.env` is git-ignored and never goes into the image, and tests ignore its key.

## Tests

- `RecommendationIntegrationTest`: WireMock stands in for profile-, catalog- and metric-service and the Anthropic API. It checks the request to the model (model, effort, schema, no sampling parameters, no user id, nickname or email), the validation of the answer, the cache and the baseline after an API error.
- Units: `LlmRankingServiceTest`, `RecommendationServiceTest`, `CandidateServiceTest`, `ExperimentServiceTest`, `PersonalDataMaskerTest`. Web layer: `RecommendationControllerTest`.
- `LiveAnthropicRankingTest` makes one real call and saves the answer to `target/live-llm-answer.json`. It costs money, so it runs only with `LIVE_LLM_TEST=true mvn -B -ntp test -Dtest=LiveAnthropicRankingTest` and a key in the environment or `.env`.
