from fastapi import APIRouter, HTTPException, Security
from app.ingestion.api import service_key
from app.generation.provider import ProviderError
from app.generation.settings import LlmSettings
from .schemas import QuizGenerationRequest, QuizGenerationResponse
from .service import QuizGenerationService

router = APIRouter(tags=['Internal Quiz Generation'])


@router.post('/internal/quizzes/generate', response_model=QuizGenerationResponse,
    dependencies=[Security(service_key)], responses={
        401: {'description': 'Invalid internal token'}, 413: {'description': 'Body too large'},
        422: {'description': 'Invalid context, INSUFFICIENT_CONTENT or TOKEN_BUDGET_EXCEEDED'},
        502: {'description': 'Invalid model output or evidence'},
        503: {'description': 'LLM unavailable'}, 504: {'description': 'Generation deadline exceeded'}})
async def generate(request: QuizGenerationRequest):
    """Generate sourced MCQs from authorized contexts; backend validates and saves the draft."""
    try:
        settings = LlmSettings.from_env()
    except ValueError:
        raise HTTPException(503, 'LLM configuration is unavailable.') from None
    try:
        return await QuizGenerationService(settings).generate(request)
    except ProviderError as error:
        raise HTTPException(error.status_code, {'code': error.code}) from None
