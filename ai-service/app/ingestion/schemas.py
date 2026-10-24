from typing import Literal

from pydantic import BaseModel


class BoundingBox(BaseModel):
    l: float
    t: float
    r: float
    b: float
    coordOrigin: str


class ContentBlock(BaseModel):
    id: str
    type: str
    text: str
    bbox: BoundingBox
    description: str | None = None
    descriptionMethod: Literal["vision_model"] | None = None


class ExtractedPage(BaseModel):
    pageNumber: int
    text: str
    blocks: list[ContentBlock]


class ExtractionResponse(BaseModel):
    documentId: int
    source: str
    pageCount: int
    pages: list[ExtractedPage]
    warnings: list[str]
