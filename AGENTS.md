# catalog-service

The catalog of educational materials from the case data, with Russian full-text search. Read-only for clients.

**Read the shared guide first:** [AGENTS.md in Documentation](https://github.com/Hack-Challenges-recommendation-system/Documentation/blob/develop/AGENTS.md) covers the project, its architecture, the rules from the case (the case data and keys never go into git; this repository is public), the commit conventions and the conventions of the Java services. Locally it is `AGENTS.md` in your clone of the Documentation repository. This file adds only what is specific to catalog-service.

## How the catalog works

- At startup `CatalogImporter` loads the file at `CATALOG_IMPORT_PATH` (e.g. `file:/data/catalog.json`) and upserts materials by `id`. An empty path skips the import.
- The file is a JSON array of `MaterialImportDto`: `id`, `title`, `summary`, `format`, `durationMinutes`, `level`, `topics`, `businessStages`, `spheres`, `paid`, `url`. Rows without `id`, `title` or `url` are skipped.
- **The case data has its own layout, which nobody has mapped yet.** Convert it to this format or add an adapter in `importer/`. Don't guess the field mapping; ask.
- Paid materials: the importer cuts `summary` to 300 characters, so it can hold only a short benefit, and the API returns it with the provided `url`.
- Search uses a generated `search_vector` column (`russian` configuration; title weight A, topics B, summary C) with a GIN index. A query matches any of its words (`plainto_tsquery` with `&` replaced by `|`) and is ranked by `ts_rank_cd`, so materials matching more words come first. This is the topic-match baseline the case compares recommendations against.
- Anthropic has no embeddings API, so there is no vector search.

## Endpoints (under `/api/v1`, all public)

| Method and path | What it does |
|---|---|
| `GET /materials` | Filters `format`, `stage`, `sphere`, `paid`; pages `page` (from 0) and `size` (1–500) |
| `GET /materials/{id}` | One material; 404 if unknown |
| `GET /materials/by-ids?ids=a&ids=b` | Up to 200 ids; keeps their order and skips unknown ones |
| `GET /materials/search?q=...&stage=...&limit=...` | `q` up to 500 characters, `limit` 1–100; returns `{material, score}` |
| `GET /health` | 200 |

## Configuration

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5437/postgres` |
| `DB_USERNAME`, `DB_PASSWORD` | `admin`, `admin` |
| `CATALOG_IMPORT_PATH` | empty: no import |

`docker compose up -d` starts Postgres on `127.0.0.1:5437`.

## Tests

- `CatalogIntegrationTest` imports the synthetic `src/test/resources/fixtures/catalog.json` into real Postgres. It checks Russian word forms, any-word matching, ranking, the stage filter, the paid summary, the order of `by-ids`, paging and validation.
- `CatalogImporterTest` covers the import rules.
