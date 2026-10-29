"""OpenAI Responses wire adapter. No SDK retries, tools, or conversation storage."""
import json
import re
import anyio
import httpx
from pydantic import BaseModel, ConfigDict, Field, ValidationError
from typing import Literal
from .schemas import GenerationResult, Evidence
from .settings import LlmSettings


class ProviderError(Exception):
    def __init__(self, status_code: int, code: str = "GENERATION_FAILED"):
        self.status_code = status_code
        self.code = code
        super().__init__(code)


class _Answer(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    status: Literal["ANSWERED", "INSUFFICIENT_CONTEXT"]
    answer: str = Field(min_length=1, max_length=32768)
    evidence: list[Evidence] = Field(max_length=20)


class LlmProvider:
    def __init__(self, settings: LlmSettings):
        self.settings = settings
        self.client = None

    async def __aenter__(self):
        self.settings.validate()
        self.client = httpx.AsyncClient(timeout=httpx.Timeout(self.settings.timeout_seconds, connect=5),
                                        follow_redirects=False)
        return self

    async def __aexit__(self, *args):
        await self.client.aclose()

    def payload(self, messages):
        return {"model": self.settings.model, "input": messages,
                "text": {"format": {"type": "json_schema", "name": "course_rag_answer", "strict": True,
                                    "schema": _Answer.model_json_schema()}}}

    async def _post(self, path, body):
        try:
            with anyio.fail_after(self.settings.timeout_seconds):
                response = await self.client.post(self.settings.base_url.rstrip("/") + path, json=body,
                    headers={"Authorization": "Bearer " + self.settings.api_key})
            if response.status_code != 200:
                status = 503 if response.status_code in {401, 403, 429, 500, 502, 503} else 504 if response.status_code == 504 else 502
                raise ProviderError(status)
            if len(response.content) > 1048576:
                raise ProviderError(502)
            return response.json()
        except (httpx.TimeoutException, TimeoutError):
            raise ProviderError(504) from None
        except httpx.RequestError:
            raise ProviderError(503) from None
        except (ValueError, UnicodeError):
            raise ProviderError(502) from None

    async def count_input_tokens(self, messages):
        data = await self._post("/responses/input_tokens", self.payload(messages))
        count = data.get("input_tokens") if isinstance(data, dict) else None
        if type(count) is not int or count < 0:
            raise ProviderError(502)
        return count

    async def generate(self, messages, max_output_tokens):
        data = await self._post("/responses", {**self.payload(messages), "store": False,
                                "truncation": "disabled", "max_output_tokens": max_output_tokens})
        return self.parse_response(data)

    def parse_response(self, data):
        try:
            if not isinstance(data, dict) or data.get("status") != "completed" or data.get("error"):
                raise ValueError()
            actual_model = data["model"]
            configured = self.settings.model
            if actual_model != configured and not re.fullmatch(re.escape(configured) + r"-\d{4}-\d{2}-\d{2}", actual_model):
                raise ValueError()
            output = data["output"]
            # Reject tools and refusals; optional reasoning items contain no answer text.
            if not isinstance(output, list) or any(item.get("type") not in {"message", "reasoning"} for item in output):
                raise ValueError()
            messages = [item for item in output if item.get("type") == "message"]
            if len(messages) != 1 or messages[0].get("role") != "assistant" or messages[0].get("status") != "completed":
                raise ValueError()
            content = messages[0]["content"]
            if len(content) != 1 or content[0].get("type") != "output_text":
                raise ValueError()
            answer = _Answer.model_validate_json(content[0]["text"])
            if not answer.answer.strip():
                raise ValueError()
            return GenerationResult(status=answer.status, answer=answer.answer.strip(), model=configured,
                                    evidence=answer.evidence)
        except (KeyError, TypeError, ValueError, AttributeError, ValidationError):
            raise ProviderError(502) from None
