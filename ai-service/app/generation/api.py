from fastapi import APIRouter, HTTPException, Security
from app.ingestion.api import service_key
from .schemas import RagAnswerRequest, RagAnswerResponse
from .settings import LlmSettings
from .service import RagService
from .provider import ProviderError

router = APIRouter(tags=["Internal RAG"])


@router.post("/internal/rag/answer", response_model=RagAnswerResponse,
             dependencies=[Security(service_key)], responses={
                 401: {"description": "Invalid internal token"}, 413: {"description": "Body too large"},
                 422: {"description": "Invalid context or TOKEN_BUDGET_EXCEEDED"},
                 502: {"description": "Invalid model output"}, 503: {"description": "LLM unavailable or unconfigured"},
                 504: {"description": "Total generation deadline exceeded"}})
async def answer(request: RagAnswerRequest):
    """Answer using backend-authorized context only; never performs retrieval or database access."""
    try:
        settings = LlmSettings.from_env()
    except ValueError:
        raise HTTPException(503, "LLM configuration is unavailable.") from None
    try:
        return await RagService(settings).answer(request)
    except ProviderError as error:
        raise HTTPException(error.status_code, {"code": error.code}) from None
