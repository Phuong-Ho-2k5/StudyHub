import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.quizzes import api
from app.quizzes.schemas import QuizGenerationResponse
from app.generation.provider import ProviderError
from test_quiz_schemas import payload, question


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv('AI_SERVICE_TOKEN', 'test-token')
    monkeypatch.setenv('LLM_API_KEY', 'test-key')
    with TestClient(app) as client: yield client


def test_auth_body_limit_and_success(client, monkeypatch):
    class Service:
        def __init__(self, settings): pass
        async def generate(self, request): return QuizGenerationResponse(model='gpt-4.1-mini', questions=[question()])
    monkeypatch.setattr(api, 'QuizGenerationService', Service)
    path = '/internal/quizzes/generate'
    headers = {'X-AI-Service-Token': 'test-token'}
    assert client.post(path, json=payload()).status_code == 401
    assert client.post(path, json=payload(contexts=[]), headers=headers).status_code == 422
    assert client.post(path, json=payload(), headers={**headers, 'Content-Length': str(8*1024*1024+1)}).status_code == 413
    assert client.post(path, json=payload(), headers=headers).json()['promptVersion'] == 'quiz-generation-v1'


@pytest.mark.parametrize('status', [422, 502, 503, 504])
def test_safe_errors(client, monkeypatch, status):
    class Service:
        def __init__(self, settings): pass
        async def generate(self, request): raise ProviderError(status)
    monkeypatch.setattr(api, 'QuizGenerationService', Service)
    result = client.post('/internal/quizzes/generate', json=payload(), headers={'X-AI-Service-Token': 'test-token'})
    assert result.status_code == status
    assert 'test-key' not in result.text
