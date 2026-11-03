import pytest
from app.quizzes.provider import QuizProvider
from app.generation.provider import ProviderError
from app.generation.settings import LlmSettings
from test_concept_provider import envelope
from test_quiz_schemas import question


def test_strict_schema_and_provider_envelope():
    provider = QuizProvider(LlmSettings(api_key='test'))
    assert provider.payload([])['text']['format']['name'] == 'quiz_generation'
    assert provider.parse_response(envelope({'status': 'GENERATED', 'questions': [question()]})).status == 'GENERATED'


@pytest.mark.parametrize('body', [envelope(status='incomplete'), envelope(model='foreign'),
    envelope({'status': 'GENERATED', 'questions': []}), envelope({'questions': [question()]}),
    envelope(output=[{'type': 'function_call'}])])
def test_invalid_response(body):
    with pytest.raises(ProviderError) as error: QuizProvider(LlmSettings(api_key='test')).parse_response(body)
    assert error.value.status_code == 502
