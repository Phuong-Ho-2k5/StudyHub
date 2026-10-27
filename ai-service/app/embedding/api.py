from threading import Lock
from typing import Annotated, Literal

from fastapi import APIRouter, HTTPException, Security
from pydantic import BaseModel, Field, StringConstraints

from app.ingestion.api import service_key
from .service import EmbeddingService
from .settings import EmbeddingSettings

router = APIRouter(tags=["Internal embeddings"])
Text = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=16384)]


class EmbeddingRequest(BaseModel):
    texts: list[Text] = Field(min_length=1, max_length=32)
    inputType: Literal["passage", "query"] = "passage"


class EmbeddingResponse(BaseModel):
    model: str
    dimensions: int
    vectors: list[list[float]]


_service_lock = Lock()
_service: EmbeddingService | None = None


def get_embedding_service(settings: EmbeddingSettings):
    global _service
    with _service_lock:
        if _service is None:
            _service = EmbeddingService(settings)
        elif _service.settings != settings:
            raise HTTPException(503, "Restart the service to change embedding configuration.")
        return _service


def close_embedding_service():
    global _service
    with _service_lock:
        if _service is not None:
            _service.close()
            _service = None


@router.post("/internal/embeddings", response_model=EmbeddingResponse,
             dependencies=[Security(service_key)], responses={
    401: {"description": "Invalid internal service token"},
    413: {"description": "Request size limit exceeded"},
    422: {"description": "Invalid batch or input exceeds the token budget"},
    503: {"description": "Missing configuration, busy or unavailable model"},
    504: {"description": "Embedding deadline exceeded"},
})
def embed_documents(request: EmbeddingRequest):
    """Embed 1–32 texts in order. Adds the E5 passage or query prefix internally; defaults to passage."""
    try:
        settings = EmbeddingSettings.from_env()
    except ValueError:
        raise HTTPException(503, "Embedding configuration is invalid.") from None
    service = get_embedding_service(settings)
    return service.embed(request.texts, input_type=request.inputType)
