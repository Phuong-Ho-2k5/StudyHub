from typing import Literal
from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel
from app.ingestion.api import ExtractionGuard, router as extraction_router
from app.embedding.api import close_embedding_service, router as embedding_router
from app.generation.api import router as generation_router


@asynccontextmanager
async def lifespan(app):
    yield
    close_embedding_service()


app = FastAPI(title="StudyHub AI Service", version="0.1.0", lifespan=lifespan)
app.add_middleware(ExtractionGuard)
app.include_router(extraction_router)
app.include_router(embedding_router)
app.include_router(generation_router)


class HealthResponse(BaseModel):
    status: Literal["UP"]
    service: Literal["studyhub-ai"]


@app.get("/health", response_model=HealthResponse, tags=["Health"])
def health() -> HealthResponse:
    return HealthResponse(status="UP", service="studyhub-ai")
