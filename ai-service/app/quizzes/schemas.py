from typing import Annotated, Literal
from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator
from app.generation.schemas import RagContext, Evidence

PROMPT_VERSION = 'quiz-generation-v1'
Text = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=500)]


class QuizGenerationRequest(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    questionCount: int = Field(ge=1, le=10)
    contexts: list[RagContext] = Field(min_length=1, max_length=20)

    @model_validator(mode='after')
    def unique_sources(self):
        if len({c.chunkId for c in self.contexts}) != len(self.contexts):
            raise ValueError('Duplicate context chunk.')
        return self


class QuizOption(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    text: Text
    correct: bool


class QuizQuestion(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    text: Text
    options: list[QuizOption] = Field(min_length=4, max_length=4)
    evidence: list[Evidence] = Field(min_length=1, max_length=3)

    @model_validator(mode='after')
    def valid_options(self):
        if sum(o.correct for o in self.options) != 1 or len({o.text.casefold() for o in self.options}) != 4:
            raise ValueError('Four distinct options and one correct answer are required.')
        return self


class QuizOutput(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    status: Literal['GENERATED', 'INSUFFICIENT_CONTENT']
    questions: list[QuizQuestion] = Field(max_length=10)

    @model_validator(mode='after')
    def valid_result(self):
        if (self.status == 'GENERATED') != bool(self.questions):
            raise ValueError('Status does not match generated questions.')
        if len({q.text.casefold() for q in self.questions}) != len(self.questions):
            raise ValueError('Duplicate question text.')
        return self


class QuizGenerationResponse(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    model: str = Field(min_length=1, max_length=200)
    promptVersion: Literal['quiz-generation-v1'] = PROMPT_VERSION
    questions: list[QuizQuestion] = Field(min_length=1, max_length=10)
