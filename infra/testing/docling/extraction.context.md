# StudyHub extraction and embedding simulation

Contract: `ai-service/openapi.json`; local handlers under this folder. Updated 2026-10-26.

## Purpose

Exercise the real Spring HTTP clients and ingestion/persistence against an AI stand-in. No cloud workspace lookup is needed: StudyHub owns both services and the existing local mock is extended from their contract.

## Execution

From repository root, start `postman simulation run infra/testing/docling/extraction.sim.yaml --port 4917 --no-history --output ndjson` in a separate terminal. The `listening` event confirms the actual URL. Set `AI_SIMULATION_URL=http://localhost:4917` in the test terminal and run `./infra/test-pgvector.ps1`; stop the simulation with Ctrl+C afterwards. Keep raw logs under ignored `logs/`.

## Assumptions

- The PostgreSQL API test needs both `PGVECTOR_TEST_URL` (provided by the disposable-database script) and `AI_SIMULATION_URL`; without either it skips. (observed 2026-10-26)
- Synthetic vectors establish the HTTP contract and database behavior, not embedding quality. Real-model smoke tests in `ai-service/tests/test_embeddings.py` passed separately on CPU. (observed 2026-10-26)
- Timeout/unavailable overrides answer before the handler, so the corresponding mock request events are absent. Judge those by the backend's 504/503 assertions, not missing handler traffic. (observed 2026-10-26)

## Observations

<!-- begin:observations -->
2026-10-27: lifecycle + Course Search verification passed on PostgreSQL: upload queue processing, manual rebuild, failed rebuild retaining a searchable published version, and delete cleanup used the real Spring clients. Success served passage/query embeddings and extraction; invalid/embed-invalid/no-chunks/html-error/query-too-long served their modeled paths. Unavailable/timeout were intercepted by conditions with no handler events. Synthetic vectors prove HTTP/SQL behavior; Vietnamese real-E5 relevance was checked separately.
2026-10-27: Task 1 query-embedding verification passed with the real Java clients: the success member received both default passage and explicit query calls; embed-invalid/html-error exercised query failures and query-too-long returned 422. Timeout/unavailable were intercepted by conditions, with no handler events. The full backend clean test run passed; this run did not execute PostgreSQL integration or establish retrieval quality.
2026-10-26: final full backend run passed, including PostgreSQL HTTP ingestion and course/workspace deletion race regressions. Success served extraction and embeddings; embed-invalid, invalid, no-chunks and html-error served their expected calls. Unavailable and timeout had no handler traffic because conditions intercepted them.
<!-- end:observations -->

## What didn't work

- 2026-10-26: the first full backend run failed OpenApiApiTest for missing 401/404 documentation on `/ingest`; adding the annotations made the next full run pass. Simulation traffic itself was valid.
