from fastapi import APIRouter, HTTPException, Security
from app.ingestion.api import service_key
from app.generation.provider import ProviderError
from app.generation.settings import LlmSettings
from .schemas import ConceptExtractionRequest, ConceptExtractionResponse
from .service import ConceptExtractionService

router = APIRouter(tags=['Internal Concepts'])


@router.post('/internal/concepts/extract', response_model=ConceptExtractionResponse,
    dependencies=[Security(service_key)], responses={
        401: {'description': 'Invalid internal token'}, 413: {'description': 'Body too large'},
        422: {'description': 'Invalid context or TOKEN_BUDGET_EXCEEDED'},
        502: {'description': 'Invalid model output'}, 503: {'description': 'LLM unavailable'},
        504: {'description': 'Total generation deadline exceeded'}})
async def extract(request: ConceptExtractionRequest):
    """Generate suggestions from authorized Document context; never save Concept entities."""
    try:
        settings = LlmSettings.from_env()
    except ValueError:
        raise HTTPException(503, 'LLM configuration is unavailable.') from None
    try:
        return await ConceptExtractionService(settings).extract(request)
    except ProviderError as error:
        raise HTTPException(error.status_code, {'code': error.code}) from None
