import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.concepts import api
from app.concepts.schemas import ConceptExtractionResponse
from app.generation.provider import ProviderError
from test_concept_schemas import payload, suggestion


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv('AI_SERVICE_TOKEN', 'test-token')
    monkeypatch.setenv('LLM_API_KEY', 'test-key')
    with TestClient(app) as client: yield client


def test_auth_validation_body_limit_and_success(client, monkeypatch):
    class Service:
        def __init__(self, settings): pass
        async def extract(self, request):
            return ConceptExtractionResponse(model='gpt-4.1-mini', suggestions=[suggestion()])
    monkeypatch.setattr(api, 'ConceptExtractionService', Service)
    path = '/internal/concepts/extract'
    headers = {'X-AI-Service-Token': 'test-token'}
    assert client.post(path, json=payload()).status_code == 401
    assert client.post(path, json={**payload(), 'contexts': []}, headers=headers).status_code == 422
    assert client.post(path, json=payload(), headers={**headers, 'Content-Length': str(8*1024*1024+1)}).status_code == 413
    response = client.post(path, json=payload(), headers=headers)
    assert response.status_code == 200
    assert response.json()['suggestions'][0]['name'] == 'Atomicity'


@pytest.mark.parametrize('status', [422, 502, 503, 504])
def test_safe_provider_errors(client, monkeypatch, status):
    class Service:
        def __init__(self, settings): pass
        async def extract(self, request): raise ProviderError(status)
    monkeypatch.setattr(api, 'ConceptExtractionService', Service)
    response = client.post('/internal/concepts/extract', json=payload(), headers={'X-AI-Service-Token': 'test-token'})
    assert response.status_code == status
    assert 'test-key' not in response.text
