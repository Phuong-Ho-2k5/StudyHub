# StudyHub external OpenAI simulation

## Purpose

Exercise the real FastAPI OpenAI HTTP adapter using the official Responses, structured-output and input-token-counting contracts. No provider secrets or live provider calls. Local fixture authoring; no external workspace mock lookup or publishing.

## Execution

`postman simulation run infra/testing/rag/rag.sim.yaml --port 4927 --no-history --output ndjson`.
Set `RAG_SIMULATION_URL` to the listening URL and run `ai-service/tests/test_rag_provider_simulation.py` using the AI virtual environment. Full Spring/PostgreSQL/FastAPI setup is in README.md.

## Observations

2026-10-27: 10 HTTP integration tests passed: valid generation, insufficient evidence, rate limit, unavailable/HTML errors, total timeout, malformed/truncated output, budget rejection and unreachable endpoint. The openai member served `/v1/responses/input_tokens` and `/v1/responses`. Count-only errors did not reach generation; timeout can log its eventual simulated response after the caller has cancelled. Unreachable endpoint was outside the simulation; Windows can report a connection deadline (504) rather than immediate connection refusal (503).

## Limits

2026-10-27 final verification: 108 AI tests passed with 7 optional skips, including all 11 provider HTTP tests; 260 Spring tests and 35 PostgreSQL integration tests passed. The real Spring/PostgreSQL/FastAPI/E5 pipeline reached count and generation endpoints and checked success, insufficient evidence, budget rejection, malformed output and rate limits. The mock now derives its answer from received context and exposes a local generation counter; tests verify one generation even on generation-stage 429, and zero generation after count-stage failures. Internal AI simulation served extraction/embedding/answer paths. No unused member was added; unavailable/timeout condition members on the internal simulation can remain silent because conditions intercept requests.

The first real FastAPI pipeline run exposed HTTP/2 cleartext-upgrade body loss from Java (422 missing body); pinning embedding/generation clients to HTTP/1.1 resolved it. Existing synthetic embedding-worker tests exceeded their three-second startup deadline because they imported the whole HTTP app in spawned test workers; import-light spawn fixtures retained the same deadlines and passed.

Synthetic token counts and answers prove orchestration/HTTP error handling, not OpenAI token accuracy, model availability or grounding quality. Live evaluation requires explicit opt-in and a real key. No simulation was deployed or pushed.

## Evidence verification (2026-10-29)

- **id:** included-context-citation-validation; **status:** supported for the local caller. **claim:** FastAPI rejects unknown/trimmed chunk IDs, fabricated quotes, duplicate or missing evidence, and requires empty evidence on insufficient context. **reason/verification:** 15 real HTTP provider-simulation tests passed, including fabricated-ID/quote/missing/duplicate cases; the controlled-budget service test rejected a citation to a trimmed chunk. **source:** `ai-service/tests/test_rag_citations.py`, `ai-service/tests/test_rag_provider_simulation.py`, `app/generation/citations.py`. Synthetic token counts establish control flow only.
- **id:** current-publication-source-resolution; **status:** supported for the local pipeline. **claim:** A citation opens its exact authorized chunk/PDF, and its old chunk ID cannot open a new publication. **reason/verification:** Spring/PostgreSQL/FastAPI/E5 pipeline passed; the collection made eight actual Spring HTTP requests and passed nine assertions including private PDF headers, quote/source matching, foreign ownership and stale source 409. Four source repository PostgreSQL tests passed including latch-controlled publish/delete during generation and failed/pending rebuilds. **source:** `CourseChatPipelineIT`, `DocumentSourceRepositoryIT`, `infra/testing/rag/collection/`.
- **id:** real-model-semantic-grounding; **status:** unknown. **claim:** The configured live model consistently returns evidence that supports every answer claim. **reason/verification:** No live provider eval ran; the mock copies context content. Exact quote validation proves provenance, not semantic entailment. **source:** opt-in `ai-service/tests/test_rag_eval.py`; backend/AI README limitations.

CLI 1.73.0; base commit `6248d2b`. Internal response and prompt version are now `course-rag-v2`; provider wire endpoints are unchanged. Simulations ran on loopback, with no deploy/push or real provider key.
