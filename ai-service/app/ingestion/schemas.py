from typing import Literal

from pydantic import BaseModel, Field


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


class ChunkSourceReference(BaseModel):
    blockId: str
    pageNumber: int
    bbox: BoundingBox


class DocumentChunk(BaseModel):
    documentId: int
    source: str
    chunkIndex: int
    content: str
    embeddingText: str
    headings: list[str]
    pageStart: int
    pageEnd: int
    blockIds: list[str]
    sourceRefs: list[ChunkSourceReference]
    overlapContent: str = ""
    overlapSourceRefs: list[ChunkSourceReference] = Field(default_factory=list)
    tokenCount: int
    tokenizerModel: str


class ExtractionResponse(BaseModel):
    documentId: int
    source: str
    pageCount: int
    pages: list[ExtractedPage]
    warnings: list[str]
    chunks: list[DocumentChunk] | None = None
