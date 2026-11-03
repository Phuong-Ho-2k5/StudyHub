import asyncio
import pytest
from app.quizzes import service
from app.quizzes.schemas import QuizGenerationRequest, QuizOutput
from app.generation.settings import LlmSettings
from app.generation.provider import ProviderError
from test_quiz_schemas import payload, question


def run(monkeypatch, questions=None, count=10, status='GENERATED', request_count=1, **settings):
    class Provider:
        calls = 0
        def __init__(self, settings): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *args): pass
        async def count_input_tokens(self, messages):
            assert 'Atomicity keeps changes atomic.' in messages[1]['content']
            return count
        async def generate(self, messages, tokens):
            Provider.calls += 1
            return QuizOutput.model_validate({'status': status, 'questions': [question()] if questions is None else questions})
    monkeypatch.setattr(service, 'QuizProvider', Provider)
    try:
        return asyncio.run(service.QuizGenerationService(LlmSettings(api_key='test', **settings)).generate(
            QuizGenerationRequest.model_validate(payload(questionCount=request_count))))
    finally:
        if count > settings.get('max_input_tokens', 999999): assert Provider.calls == 0


def test_generation_returns_metadata(monkeypatch):
    result = run(monkeypatch)
    assert result.promptVersion == 'quiz-generation-v1'
    assert len(result.questions) == 1


@pytest.mark.parametrize('evidence', [[{'chunkId': 99, 'quote': 'Atomicity'}],
    [{'chunkId': 1, 'quote': 'Invented'}], [{'chunkId': 1, 'quote': 'Atomicity'}] * 2])
def test_false_or_duplicate_sources(monkeypatch, evidence):
    with pytest.raises(ProviderError) as error: run(monkeypatch, questions=[question(evidence=evidence)])
    assert error.value.status_code == 502


def test_insufficient_and_count_mismatch(monkeypatch):
    with pytest.raises(ProviderError) as error: run(monkeypatch, status='INSUFFICIENT_CONTENT', questions=[])
    assert (error.value.status_code, error.value.code) == (422, 'INSUFFICIENT_CONTENT')
    with pytest.raises(ProviderError) as error: run(monkeypatch, request_count=2)
    assert error.value.status_code == 502


def test_budget_checked_before_generation(monkeypatch):
    with pytest.raises(ProviderError) as error: run(monkeypatch, count=100, max_input_tokens=50)
    assert (error.value.status_code, error.value.code) == (422, 'TOKEN_BUDGET_EXCEEDED')
