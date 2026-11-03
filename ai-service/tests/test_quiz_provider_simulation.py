import asyncio
import os
import httpx
import pytest
from app.quizzes.schemas import QuizGenerationRequest
from app.quizzes.service import QuizGenerationService
from app.generation.provider import ProviderError
from app.generation.settings import LlmSettings
from test_quiz_schemas import payload

pytestmark = pytest.mark.skipif(not os.getenv('QUIZ_SIMULATION_URL'), reason='Requires local Quiz provider simulation')


def settings(**changes):
    return LlmSettings(api_key='simulation-key', base_url=os.environ['QUIZ_SIMULATION_URL'] + '/openai/v1', **changes)


def test_real_http_success():
    result = asyncio.run(QuizGenerationService(settings()).generate(QuizGenerationRequest.model_validate(payload())))
    assert result.questions[0].evidence[0].quote == 'Atomicity keeps changes atomic.'
    assert result.questions[0].evidence[0].chunkId == 1


@pytest.mark.parametrize('marker,status', [('fake-id', 502), ('fake-quote', 502), ('malformed', 502),
    ('bad-options', 502), ('truncated', 502), ('insufficient', 422), ('budget', 422),
    ('unavailable', 503), ('rate-limit', 503), ('timeout', 504)])
def test_real_http_failure_paths(marker, status):
    body = payload()
    body['contexts'][0]['content'] = f'Atomicity [{marker}]'
    with pytest.raises(ProviderError) as error:
        asyncio.run(QuizGenerationService(settings(timeout_seconds=0.3 if marker == 'timeout' else 5)).generate(
            QuizGenerationRequest.model_validate(body)))
    assert error.value.status_code == status


def test_budget_and_rate_limit_do_not_retry():
    for marker in ['budget', 'rate-limit']:
        body = payload()
        content = f'Atomicity [{marker}] no-retry-{os.urandom(4).hex()}'
        body['contexts'][0]['content'] = content
        with pytest.raises(ProviderError):
            asyncio.run(QuizGenerationService(settings()).generate(QuizGenerationRequest.model_validate(body)))
        stats = httpx.get(os.environ['QUIZ_SIMULATION_URL'] + '/openai/_stats').json()['counts']
        assert stats.get(content, 0) == (0 if marker == 'budget' else 1)
