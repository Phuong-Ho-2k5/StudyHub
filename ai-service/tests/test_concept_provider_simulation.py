import asyncio
import os
import httpx
import pytest
from app.concepts.schemas import ConceptExtractionRequest
from app.concepts.service import ConceptExtractionService
from app.generation.provider import ProviderError
from app.generation.settings import LlmSettings
from test_concept_schemas import payload

pytestmark = pytest.mark.skipif(not os.getenv('CONCEPT_SIMULATION_URL'), reason='Requires local Concept simulation')


def settings(**changes):
    return LlmSettings(api_key='simulation-key', base_url=os.environ['CONCEPT_SIMULATION_URL'] + '/openai/v1', **changes)


def test_real_http_success_uses_context_and_keeps_schema():
    result = asyncio.run(ConceptExtractionService(settings()).extract(ConceptExtractionRequest.model_validate(payload())))
    assert result.suggestions[0].evidence[0].quote == 'Atomicity keeps a transaction atomic.'
    assert result.suggestions[0].evidence[0].chunkId == 1


@pytest.mark.parametrize('marker,status', [('fake-id', 502), ('fake-quote', 502), ('malformed', 502),
    ('truncated', 502), ('budget', 422), ('unavailable', 503), ('rate-limit', 503), ('timeout', 504)])
def test_real_http_failure_paths(marker, status):
    body = payload()
    body['contexts'][0]['content'] = f'Atomicity [{marker}]'
    with pytest.raises(ProviderError) as error:
        asyncio.run(ConceptExtractionService(settings(timeout_seconds=0.3 if marker == 'timeout' else 5)).extract(
            ConceptExtractionRequest.model_validate(body)))
    assert error.value.status_code == status


def test_budget_does_not_generate_and_rate_limit_does_not_retry():
    for marker in ['budget', 'rate-limit']:
        body = payload()
        content = f'Atomicity [{marker}] no-retry-{os.urandom(4).hex()}'
        body['contexts'][0]['content'] = content
        with pytest.raises(ProviderError):
            asyncio.run(ConceptExtractionService(settings()).extract(ConceptExtractionRequest.model_validate(body)))
        stats = httpx.get(os.environ['CONCEPT_SIMULATION_URL'] + '/openai/_stats').json()['counts']
        assert stats.get(content, 0) == (0 if marker == 'budget' else 1)
