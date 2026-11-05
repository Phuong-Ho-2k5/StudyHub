from pydantic import ValidationError
from app.generation.provider import LlmProvider, ProviderError
from .schemas import AdviceOutput


class AdviceProvider(LlmProvider):
    def payload(self, messages):
        return {"model": self.settings.model, "input": messages,
                "text": {"format": {"type": "json_schema", "name": "learning_advice", "strict": True,
                                    "schema": AdviceOutput.model_json_schema()}}}

    def parse_response(self, data):
        try:
            return AdviceOutput.model_validate_json(self.output_text(data))
        except (ValidationError, ValueError, TypeError):
            raise ProviderError(502) from None
