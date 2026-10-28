import math
import os
from dataclasses import dataclass, field
from urllib.parse import urlsplit


@dataclass(frozen=True)
class LlmSettings:
    provider: str = "openai"
    base_url: str = "https://api.openai.com/v1"
    api_key: str = field(default="", repr=False)
    model: str = "gpt-4.1-mini"
    timeout_seconds: float = 60
    max_input_tokens: int = 8192
    max_output_tokens: int = 1024
    context_window_tokens: int = 32768

    def validate(self):
        url = urlsplit(self.base_url)
        local = url.hostname in {"127.0.0.1", "localhost", "::1"}
        if (self.provider != "openai" or not self.api_key.strip() or not self.model.strip()
                or len(self.model) > 200 or url.scheme not in {"http", "https"}
                or not url.hostname or (url.scheme != "https" and not local)
                or url.username or url.password or url.query or url.fragment
                or not math.isfinite(self.timeout_seconds) or not 0 < self.timeout_seconds <= 60
                or not 1 <= self.max_input_tokens <= 1047552
                or not 1 <= self.max_output_tokens <= 32768
                or self.max_input_tokens + self.max_output_tokens > self.context_window_tokens):
            raise ValueError("Invalid LLM configuration.")
        return self

    @classmethod
    def from_env(cls):
        return cls(provider=os.getenv("LLM_PROVIDER", "openai"),
                   base_url=os.getenv("LLM_BASE_URL", "https://api.openai.com/v1").rstrip("/"),
                   api_key=os.getenv("LLM_API_KEY", ""), model=os.getenv("LLM_MODEL", "gpt-4.1-mini"),
                   timeout_seconds=float(os.getenv("LLM_TIMEOUT_SECONDS", "60")),
                   max_input_tokens=int(os.getenv("RAG_MAX_INPUT_TOKENS", "8192")),
                   max_output_tokens=int(os.getenv("LLM_MAX_OUTPUT_TOKENS", "1024")),
                   context_window_tokens=int(os.getenv("LLM_CONTEXT_WINDOW_TOKENS", "32768"))).validate()
