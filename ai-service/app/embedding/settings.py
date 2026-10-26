import os
import re
from dataclasses import dataclass


@dataclass(frozen=True)
class EmbeddingSettings:
    model: str = "intfloat/multilingual-e5-small"
    revision: str = "614241f622f53c4eeff9890bdc4f31cfecc418b3"
    dimensions: int = 384
    timeout_seconds: int = 120

    def __post_init__(self):
        if self.model != "intfloat/multilingual-e5-small":
            raise ValueError("This adapter supports multilingual-e5-small only")
        if not re.fullmatch(r"[a-f0-9]{40}", self.revision):
            raise ValueError("Embedding revision must be an immutable commit SHA")
        if not 1 <= self.dimensions <= 4096 or self.timeout_seconds <= 0:
            raise ValueError("Invalid embedding dimensions or timeout")

    @property
    def identity(self):
        return f"{self.model}@{self.revision}"

    @classmethod
    def from_env(cls):
        return cls(
            model=os.getenv("AI_EMBEDDING_MODEL", cls.model),
            revision=os.getenv("AI_EMBEDDING_REVISION", cls.revision),
            dimensions=int(os.getenv("AI_EMBEDDING_DIMENSIONS", str(cls.dimensions))),
            timeout_seconds=int(os.getenv("AI_EMBEDDING_TIMEOUT_SECONDS", str(cls.timeout_seconds))),
        )
