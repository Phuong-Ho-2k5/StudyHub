from fastapi import APIRouter, HTTPException, Security
from app.ingestion.api import service_key
from app.generation.settings import LlmSettings
from app.generation.provider import ProviderError
from .schemas import AdviceRequest, AdviceResponse
from .service import AdviceService

router = APIRouter(tags=["Internal learning advice"])


@router.post("/internal/advice/generate", response_model=AdviceResponse,
             dependencies=[Security(service_key)], responses={
                 401: {"description": "Invalid internal token"}, 413: {"description": "Body too large"},
                 422: {"description": "Invalid context or token budget exceeded"},
                 502: {"description": "Invalid model output"}, 503: {"description": "LLM unavailable"},
                 504: {"description": "Total generation deadline exceeded"}})
async def generate(request: AdviceRequest):
    """Generate from backend-authorized facts and sources; never reads the database."""
    try:
        settings = LlmSettings.from_env()
    except ValueError:
        raise HTTPException(503, "LLM configuration is unavailable.") from None
    try:
        return await AdviceService(settings).generate(request)
    except ProviderError as error:
        raise HTTPException(error.status_code, {"code": error.code}) from None
