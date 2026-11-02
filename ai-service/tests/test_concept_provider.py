import json
import pytest
from app.concepts.provider import ConceptProvider
from app.generation.provider import ProviderError
from app.generation.settings import LlmSettings
from test_concept_schemas import suggestion


def envelope(output=None, **changes):
    return {'status': 'completed', 'model': 'gpt-4.1-mini-2025-04-14', 'output': [
        {'type': 'message', 'role': 'assistant', 'status': 'completed', 'content': [
            {'type': 'output_text', 'text': json.dumps(output if output is not None else {'suggestions': [suggestion()]})}]}], **changes}


def test_schema_for_count_and_generation_is_concept_schema():
    provider = ConceptProvider(LlmSettings(api_key='test-key'))
    payload = provider.payload([])
    assert payload['text']['format']['schema']['properties']['suggestions']['type'] == 'array'
    assert payload['text']['format']['name'] == 'document_concepts'
    assert provider.parse_response(envelope()).suggestions[0].name == 'Atomicity'


@pytest.mark.parametrize('body', [envelope(status='incomplete'), envelope(model='other'),
    envelope(output=[{'type': 'function_call'}]), envelope(output=[{'type': 'message', 'role': 'assistant',
        'status': 'completed', 'content': [{'type': 'refusal', 'refusal': 'private'}]}]),
    envelope({'suggestions': [suggestion(evidence=[])]}), envelope({'suggestions': [], 'confidence': 100})])
def test_rejects_refusals_tools_truncation_and_wrong_schema(body):
    with pytest.raises(ProviderError) as error:
        ConceptProvider(LlmSettings(api_key='test-key')).parse_response(body)
    assert error.value.status_code == 502
