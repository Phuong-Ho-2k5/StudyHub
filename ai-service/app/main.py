from typing import Literal

from fastapi import FastAPI
from pydantic import BaseModel
from app.ingestion.api import ExtractionGuard, router as extraction_router

app = FastAPI(title="StudyHub AI Service", version="0.1.0")
app.add_middleware(ExtractionGuard)
app.include_router(extraction_router)


class HealthResponse(BaseModel):
    status: Literal["UP"]
    service: Literal["studyhub-ai"]


@app.get("/health", response_model=HealthResponse, tags=["Health"])
def health() -> HealthResponse:
    return HealthResponse(status="UP", service="studyhub-ai")
