import asyncio
import pytest
from app.concepts import service
from app.concepts.schemas import ConceptExtractionRequest, ConceptExtractionResponse
from app.generation.settings import LlmSettings
from app.generation.provider import ProviderError
from test_concept_schemas import payload, suggestion


def run(monkeypatch, suggestions=None, count=10, **settings):
    class FakeProvider:
        generated = 0
        def __init__(self, settings): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *args): pass
        async def count_input_tokens(self, messages):
            assert 'Atomicity keeps a transaction atomic.' in messages[1]['content']
            return count
        async def generate(self, messages, tokens):
            FakeProvider.generated += 1
            return ConceptExtractionResponse(model='gpt-4.1-mini', suggestions=suggestions if suggestions is not None else [suggestion()])
    monkeypatch.setattr(service, 'ConceptProvider', FakeProvider)
    result = asyncio.run(service.ConceptExtractionService(LlmSettings(api_key='test', **settings)).extract(
        ConceptExtractionRequest.model_validate(payload())))
    return result, FakeProvider.generated


def test_extract_keeps_sources_and_calls_generation_once(monkeypatch):
    result, calls = run(monkeypatch)
    assert result.suggestions[0].name == 'Atomicity'
    assert result.suggestions[0].evidence[0].chunkId == 1
    assert result.promptVersion == 'document-concepts-v1'
    assert calls == 1


def test_empty_result(monkeypatch):
    assert run(monkeypatch, suggestions=[])[0].suggestions == []


@pytest.mark.parametrize('evidence', [[{'chunkId': 99, 'quote': 'Atomicity'}],
    [{'chunkId': 1, 'quote': 'invented'}], [{'chunkId': 1, 'quote': 'Atomicity'}] * 2])
def test_rejects_false_or_duplicate_evidence(monkeypatch, evidence):
    with pytest.raises(ProviderError) as error:
        run(monkeypatch, suggestions=[suggestion(evidence=evidence)])
    assert error.value.status_code == 502


def test_rejects_excess_suggestions(monkeypatch):
    with pytest.raises(ProviderError) as error:
        run(monkeypatch, suggestions=[suggestion(name=str(i)) for i in range(6)])
    assert error.value.status_code == 502


def test_budget_overflow_is_not_silent_truncation(monkeypatch):
    with pytest.raises(ProviderError) as error:
        run(monkeypatch, count=100, max_input_tokens=50)
    assert error.value.status_code == 422
    assert error.value.code == 'TOKEN_BUDGET_EXCEEDED'
