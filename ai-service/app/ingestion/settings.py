import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class ExtractionSettings:
    max_file_bytes: int
    max_pages: int
    timeout_seconds: int
    picture_descriptions: bool
    picture_model: str
    model_cache_dir: str

    @classmethod
    def from_env(cls):
        values = cls(
            max_file_bytes=int(os.getenv("AI_MAX_FILE_BYTES", "20971520")),
            max_pages=int(os.getenv("AI_MAX_PAGES", "200")),
            timeout_seconds=int(os.getenv("AI_EXTRACTION_TIMEOUT_SECONDS", "180")),
            picture_descriptions=os.getenv("AI_PICTURE_DESCRIPTIONS", "false").lower() == "true",
            picture_model=os.getenv("AI_PICTURE_MODEL", "HuggingFaceTB/SmolVLM-256M-Instruct"),
            model_cache_dir=os.getenv("AI_MODEL_CACHE_DIR", str(Path.home() / ".cache")),
        )
        if min(values.max_file_bytes, values.max_pages, values.timeout_seconds) <= 0:
            raise ValueError("Extraction limits must be positive")
        return values
