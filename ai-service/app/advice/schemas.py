from typing import Annotated, Any, Literal
from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator
from app.generation.schemas import RagContext, Evidence

PROMPT_VERSION = "learning-advice-v1"
INSUFFICIENT_ANSWER = "Chưa đủ dữ liệu học tập để đưa ra lời khuyên cá nhân hóa cho khóa học này."
FactId = Annotated[str, StringConstraints(min_length=1, max_length=100)]


class LearningFact(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    id: FactId
    type: Literal["PROGRESS", "CONCEPT", "PREREQUISITE", "QUIZ_ATTEMPT", "STUDY_SUMMARY"]
    data: dict[str, Any]


class AdviceRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    question: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=2000)]
    learningEvidence: list[LearningFact] = Field(min_length=1, max_length=37)
    contexts: list[RagContext] = Field(max_length=10)

    @model_validator(mode="after")
    def unique_inputs(self):
        if len({f.id for f in self.learningEvidence}) != len(self.learningEvidence):
            raise ValueError("Duplicate learning fact.")
        if len({c.chunkId for c in self.contexts}) != len(self.contexts):
            raise ValueError("Duplicate context.")
        return self


class AdviceOutput(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    status: Literal["ANSWERED", "LEARNING_DATA_ONLY", "INSUFFICIENT_CONTEXT"]
    answer: str = Field(min_length=1, max_length=32768)
    usedLearningEvidenceIds: list[FactId] = Field(max_length=37)
    evidence: list[Evidence] = Field(max_length=10)

    @model_validator(mode="after")
    def nonblank_answer(self):
        if not self.answer.strip():
            raise ValueError("Answer must contain text.")
        return self


class AdviceResponse(AdviceOutput):
    model: str
    promptVersion: Literal["learning-advice-v1"] = PROMPT_VERSION
