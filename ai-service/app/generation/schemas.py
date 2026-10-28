from typing import Annotated, Literal
from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

PROMPT_VERSION = "course-rag-v1"
INSUFFICIENT_ANSWER = "Không đủ thông tin trong tài liệu của khóa học để trả lời câu hỏi này."
Question = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=16384)]


class RagContext(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    chunkId: int = Field(gt=0)
    documentId: int = Field(gt=0)
    documentTitle: str = Field(min_length=1, max_length=200)
    chunkIndex: int = Field(ge=0)
    content: str = Field(min_length=1, max_length=65536)
    pageStart: int | None = Field(default=None, gt=0)
    pageEnd: int | None = Field(default=None, gt=0)

    @model_validator(mode="after")
    def valid_source(self):
        if not self.content.strip() or not self.documentTitle.strip():
            raise ValueError("Context must contain text and a title.")
        if (self.pageStart is None) != (self.pageEnd is None):
            raise ValueError("Pages must both be present or absent.")
        if self.pageStart is not None and self.pageEnd < self.pageStart:
            raise ValueError("Page range is reversed.")
        return self


class RagAnswerRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    question: Question
    contexts: list[RagContext] = Field(min_length=1, max_length=20)


class GenerationResult(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    status: Literal["ANSWERED", "INSUFFICIENT_CONTEXT"]
    answer: str = Field(min_length=1, max_length=32768)
    model: str


class RagAnswerResponse(GenerationResult):
    promptVersion: Literal["course-rag-v1"] = PROMPT_VERSION
