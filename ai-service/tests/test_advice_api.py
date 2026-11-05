import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.advice import api
from app.advice.schemas import AdviceResponse
from app.generation.provider import ProviderError
from test_advice_service import payload, output


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_TOKEN", "test-token")
    monkeypatch.setenv("LLM_API_KEY", "test-key")
    with TestClient(app) as client: yield client


def test_auth_validation_and_success(client, monkeypatch):
    class Service:
        def __init__(self, settings): pass
        async def generate(self, request): return AdviceResponse(**output(), model="gpt-4.1-mini")
    monkeypatch.setattr(api, "AdviceService", Service)
    path = "/internal/advice/generate"
    headers = {"X-AI-Service-Token": "test-token"}
    assert client.post(path, json=payload()).status_code == 401
    assert client.post(path, json=payload(question=False), headers=headers).status_code == 422
    assert client.post(path, json=payload(), headers={**headers,"Content-Length": str(8*1024*1024+1)}).status_code == 413
    result = client.post(path, json=payload(), headers=headers)
    assert result.status_code == 200
    assert result.json()["promptVersion"] == "learning-advice-v1"


@pytest.mark.parametrize("status", [422,502,503,504])
def test_safe_provider_errors(client, monkeypatch, status):
    class Service:
        def __init__(self, settings): pass
        async def generate(self, request): raise ProviderError(status)
    monkeypatch.setattr(api,"AdviceService",Service)
    result = client.post("/internal/advice/generate", json=payload(), headers={"X-AI-Service-Token":"test-token"})
    assert result.status_code == status
    assert "test-key" not in result.text
