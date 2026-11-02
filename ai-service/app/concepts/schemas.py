from typing import Annotated, Literal
from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator
from app.generation.schemas import RagContext, Evidence

PROMPT_VERSION = 'document-concepts-v1'
Name = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=150)]
Description = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=1000)]


class ConceptExtractionRequest(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    maxSuggestions: int = Field(default=5, ge=1, le=10)
    contexts: list[RagContext] = Field(min_length=1, max_length=20)

    @model_validator(mode='after')
    def single_document(self):
        if len({c.documentId for c in self.contexts}) != 1 or len({c.chunkId for c in self.contexts}) != len(self.contexts):
            raise ValueError('Contexts must have unique chunk IDs and one Document.')
        return self


class ConceptSuggestion(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    name: Name
    description: Description
    evidence: list[Evidence] = Field(min_length=1, max_length=3)


class ConceptOutput(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    suggestions: list[ConceptSuggestion] = Field(max_length=10)

    @model_validator(mode='after')
    def unique_names(self):
        if len({s.name for s in self.suggestions}) != len(self.suggestions):
            raise ValueError('Duplicate suggestion names.')
        return self


class ConceptExtractionResponse(ConceptOutput):
    model: str = Field(min_length=1, max_length=200)
    promptVersion: Literal['document-concepts-v1'] = PROMPT_VERSION
