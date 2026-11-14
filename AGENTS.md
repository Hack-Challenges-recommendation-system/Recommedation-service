# recommendation-service

Builds the personal list of materials. The vendor LLM ranks and explains; this service supplies the context, checks the answer and logs what was shown.

**Status:** an empty Spring Boot application. The team builds it after the frontend; until then, change it only when asked.

**Read the shared guide first:** [AGENTS.md in Documentation](https://github.com/Hack-Challenges-recommendation-system/Documentation/blob/develop/AGENTS.md) covers the project, its architecture, the rules from the case (the case data and keys never go into git; this repository is public), the commit conventions and the conventions of the Java services. Locally it is `AGENTS.md` in your clone of the Documentation repository. This file adds only what is specific to recommendation-service.

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
