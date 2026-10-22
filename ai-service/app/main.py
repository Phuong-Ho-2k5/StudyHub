from typing import Literal

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="StudyHub AI Service", version="0.1.0")


class HealthResponse(BaseModel):
    status: Literal["UP"]
    service: Literal["studyhub-ai"]


@app.get("/health", response_model=HealthResponse, tags=["Health"])
def health() -> HealthResponse:
    return HealthResponse(status="UP", service="studyhub-ai")
