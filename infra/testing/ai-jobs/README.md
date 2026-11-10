# Durable AI jobs

AI jobs record a long-running operation independently of an HTTP request. They do not execute work. The backend exposes the read API, durable asynchronous producers and background workers for ingestion, Concept drafts and Quiz drafts. No provider credentials are needed for these tests.

## Storage and lifecycle

Migration V28 creates `ai_jobs`. Each UUID job stores `requested_by`, `course_id`, optional `document_id`, a type, state, timestamps, and either a result reference or an allowlisted error code. User/Course/Document deletion cascades to jobs. Indexes cover requester/Course/history and state/creation order. There is no backfill or change to `document_ingestions`, published ingestion IDs or chunks.

Types and matching results:

| Job type | Result type | Document required |
| --- | --- | --- |
| INGEST_DOCUMENT | DOCUMENT_INDEX | Yes |
| EXTRACT_CONCEPTS | CONCEPT_SUGGESTION_BATCH | No |
| GENERATE_QUIZ | QUIZ | No |

The only transitions are `PENDING ? PROCESSING ? COMPLETED/FAILED`. Pending has no claim, start/finish, result or error. Processing has a claim token and start time. Completed has a matching result type/ID and finish time; Failed has an allowlisted error code and finish time. Terminal states clear the token. Database checks enforce these invariants and timestamp order. Result IDs must be nonblank and at most 100 characters.

`AIJobService.enqueue(CreateAIJob, userId)` validates ownership and document membership under User ? Workspace ? Course ? Document locks, then inserts Pending. `claim(jobId)` rechecks ownership and conditionally moves Pending to Processing, returning a server-generated token to the internal caller. Competing claims have one winner. `complete(claim, result)` and `fail(claim, code)` require Processing plus the same token and current ownership. Late/duplicate/invalid claims return false without changing the snapshot. Job types and error codes are enums; job rows never contain prompts, output content, exception messages, paths or secrets.

Enqueue and finalization join the caller transaction. Perform AI/HTTP/PDF work outside database locks. The publication caller must call `complete` in the output transaction and roll back all output when it returns false. Deletion/ownership changes serialize with the scope locks. A completed job only records execution success; it does not itself set `Document.ingestionStatus=INDEXED` or publish chunks.

Allowed failure codes: `AI_UNAVAILABLE`, `AI_TIMEOUT`, `AI_INVALID_RESPONSE`, `NO_CONTENT`, `SOURCE_CHANGED`, `JOB_FAILED`.

## HTTP contract

`GET /api/ai/jobs/{jobId}` requires Bearer JWT. Both the original requester and current ownership of the Course's Workspace must match. Admin has no bypass. Reads neither claim a job nor call AI.

```http
GET /api/ai/jobs/5d0b41ee-9f4d-42ec-94ed-216d29cbdbca
Authorization: Bearer <accessToken>
```

```json
{
  "id": "5d0b41ee-9f4d-42ec-94ed-216d29cbdbca",
  "type": "GENERATE_QUIZ",
  "status": "COMPLETED",
  "courseId": 10,
  "documentId": null,
  "createdAt": "2026-11-09T00:00:00Z",
  "updatedAt": "2026-11-09T00:00:05Z",
  "startedAt": "2026-11-09T00:00:01Z",
  "finishedAt": "2026-11-09T00:00:05Z",
  "result": {"type": "QUIZ", "id": "42"},
  "errorCode": null
}
```

Timestamps serialize as ISO-8601 UTC. Result is null except for Completed; errorCode is null except for Failed. Requester/claim token and storage paths are never returned. Result IDs are references; the business result API must independently check ownership and return 404 for a deleted result. There is no progress percentage, public job creation, status mutation, list, retry or cancel API.

| Code | Meaning |
| --- | --- |
| 200 | Current snapshot |
| 400 | Malformed UUID |
| 401 | Missing/invalid JWT |
| 404 | Missing job, different requester, or lost Course ownership |

Errors use the existing `ApiErrorResponse` envelope. OpenAPI is available at `/v3/api-docs` and Swagger UI.

## Verification

From `backend`:

```powershell
.\mvnw.cmd '-Dtest=AIJob*Test,DocumentIngestionQueueTest,DocumentIngestionServiceTest,OpenApiApiTest' test
.\mvnw.cmd test
```

From the repository root:

```powershell
.\infra\testing\ai-jobs\test-postgres.ps1
```

The runner starts the existing local Postman extraction simulation on a free port unless `AI_SIMULATION_URL` is already set; Postman CLI 1.70+ is required for automatic startup. It stops only the process it starts and restores the previous simulation URL. This serves synthetic AI responses and never calls a paid provider. The runner uses an explicitly supplied, dedicated TEST database through all three `PGVECTOR_TEST_URL`, `PGVECTOR_TEST_USER`, `PGVECTOR_TEST_PASSWORD` variables; otherwise it starts a disposable pgvector container bound to loopback. Never supply a production or development database containing data to preserve. Tests create random schemas and clean up only their own schemas. The runner removes only its own container and restores prior environment variables. Missing Docker/database, stale reports, failures and skipped integration tests cannot produce a successful acceptance result.

The PostgreSQL checks cover upgrade from the previous schema with an existing published index and pending ingestion, repeat migrations, competing claims, competing completion/failure, deletion/ownership changes before and after publication locks, and the existing ingestion migration/API regressions. HTTP tests exercise real JWT filters and the server, matching the repository's API-test convention.

## Async contract

The following POSTs return **202** with `{jobId,statusUrl}` and `Location: /api/ai/jobs/{jobId}`:

- `/api/documents/{documentId}/ingest`
- `/api/courses/{courseId}/documents/{documentId}/ai/concept-suggestions`
- `/api/documents/{documentId}/ai/generate-quiz`
- `/api/courses/{courseId}/ai/generate-quiz`

Upload still returns **201** after storing the PDF and enqueueing ingestion in the same transaction. Document responses include nullable `ingestionJobId`. Active re-ingestion returns 409; follow the existing ID from GET Document. Generation request schemas and limits are unchanged. Input/scope errors still reject before enqueue; provider/schema/budget errors after acceptance appear as FAILED plus an allowlisted errorCode when polling the job, rather than synchronous 5xx responses.

Quiz POSTs require Idempotency-Key. A reservation is persisted before execution: same requester/key/normalized payload returns the same job ID in any state; a different payload returns 409. A failed job requires a new key for a new generation. Deleted Quiz results retain the existing 409 replay behavior. Successful pre-V29 quiz generations acquire a completed job on replay without another model call.

V29 adds `ai_job_commands` and ingestion-to-job links. Commands persist request parameters and selected published ingestion versions, never prompts, PDF content or credentials. Stop old workers before migrating; mixed-version rolling deployment is unsupported. Active ingestions are backfilled without changing ingestion IDs, published pointers or chunks. Ingestion remains executed only by DocumentIngestionWorker; the generation worker only scans generation commands.

Ingestion lease reclaim retains one public PROCESSING job while its ingestion token fences obsolete attempts. Output and terminal job state commit atomically; failed completion rolls back output. Generation calls AI outside transactions and validates the queued source versions before generation and again when saving. Polling reads never call AI.

Worker settings:

```yaml
studyhub:
  ingestion:
    worker-enabled: true
    poll-delay-ms: 1000
  ai:
    jobs:
      worker-enabled: true
      poll-delay-ms: 1000
```

**Current recovery boundary:** generation PENDING commands survive restart. Generation PROCESSING jobs do not yet have automatic crash recovery, retry/backoff or a job-level deadline; those are planned for a later resilience phase. Existing ingestion leases/max-attempt recovery remain active. Exactly-once model billing is not guaranteed.

Frontend: Course documents offer Concept suggestions and AI Quiz generation; the Quiz tab offers generation from 1–5 published Documents. An accepted generation is tracked in the `job` URL search parameter, so refresh resumes it. The status component verifies Course scope, polls sequentially every three seconds, pauses while hidden and stops at terminal state or authentication/scope failure. Read errors can retry GET, never generation POST. Results open the existing Quiz review or a sourced Concept batch; confirmation remains an explicit user action. New ingestion rows use job polling; legacy rows fall back to Document polling.

Collection checks:

```powershell
postman collection lint infra/testing/ai-jobs/collection
postman collection lint infra/testing/concepts/collection
postman collection lint infra/testing/quiz-generation/collection
```

`test-postgres.ps1` always runs job migration/claim/publication/reservation concurrency plus ingestion regressions. If CONCEPT_PIPELINE_AI_URL and/or QUIZ_PIPELINE_AI_URL point to local FastAPI instances backed by the corresponding Postman provider simulation, it also runs those pipeline and concurrency suites. RUN_CONCEPT_COLLECTION=1 / RUN_QUIZ_COLLECTION=1 enable real collection runs on disposable fixture JWTs. The quiz pipeline also runs the AI-job ownership collection. Collections use bounded status polling and then read the business result; their execution, not lint alone, verifies assertions. See the Concepts/Quiz README for simulation startup instructions.

## Historical verification record

Verified on 2026-11-09 with no paid provider calls:

- Focused backend/API/OpenAPI/ingestion checks before review: 47 passed, zero failures/errors/skips.
- Final PostgreSQL runner: 29 AIJob unit/API tests and 14 integration tests passed, zero failures/errors/skips. Postman request logs confirmed extraction, embedding and RAG traffic for the existing ingestion regression.
- Full `mvnw.cmd test`: 404 tests, 389 passed, 15 skipped, zero failures/errors. Skips were existing simulation tests without `AI_SIMULATION_URL` (12), opt-in feedback/recommendation collection tests (2), and the environment-dependent file/symlink check (1). The PostgreSQL runner separately exercised ingestion against its automatically started simulation.
- `git diff --check` passed. Independent review findings were addressed with publication transaction/rollback coverage, strict whitespace references, admin scope coverage and exact PostgreSQL backend-PID lock observation.

These results establish persistence, authorization, lifecycle concurrency and the existing ingestion HTTP/SQL behavior against synthetic AI responses. Async producer/worker execution and recovery remain future integration work.
