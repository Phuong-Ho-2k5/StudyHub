from pydantic import ValidationError
from app.generation.provider import LlmProvider, ProviderError
from .schemas import ConceptOutput, ConceptExtractionResponse


class ConceptProvider(LlmProvider):
    def payload(self, messages):
        return {'model': self.settings.model, 'input': messages,
            'text': {'format': {'type': 'json_schema', 'name': 'document_concepts', 'strict': True,
                'schema': ConceptOutput.model_json_schema()}}}

    def parse_response(self, data):
        try:
            output = ConceptOutput.model_validate_json(self.output_text(data))
            return ConceptExtractionResponse(model=self.settings.model, suggestions=output.suggestions)
        except (ValidationError, ValueError, TypeError):
            raise ProviderError(502) from None
