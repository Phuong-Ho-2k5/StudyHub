"""Real FastAPI -> OpenAI HTTP adapter -> Postman simulation, never live OpenAI."""
import asyncio
import os
import pytest
import httpx
from fastapi.testclient import TestClient
from app.main import app
from app.generation.provider import LlmProvider, ProviderError
from app.generation.settings import LlmSettings
from app.generation.schemas import INSUFFICIENT_ANSWER

pytestmark=pytest.mark.skipif(not os.getenv("RAG_SIMULATION_URL"),reason="Requires local Postman RAG simulation")


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_TOKEN","simulation-token")
    monkeypatch.setenv("LLM_API_KEY","simulation-key")
    monkeypatch.setenv("LLM_MODEL","gpt-4.1-mini")
    monkeypatch.setenv("LLM_BASE_URL",os.getenv("RAG_SIMULATION_URL", "")+"/openai/v1")
    monkeypatch.setenv("LLM_TIMEOUT_SECONDS","1")
    with TestClient(app) as c: yield c


def ask(client, question):
    return client.post("/internal/rag/answer",headers={"X-AI-Service-Token":"simulation-token"},json={
        "question":question,"contexts":[{"chunkId":1,"documentId":2,"documentTitle":"DB","chunkIndex":0,
            "content":"Transaction bảo đảm tính nguyên tử (atomicity).","pageStart":1,"pageEnd":1}]})


def generation_count(question):
    r=httpx.get(os.environ['RAG_SIMULATION_URL']+'/openai/_stats',timeout=5)
    r.raise_for_status()
    return r.json()['generationCounts'].get(question,0)


def test_full_generation_http_contract(client):
    before=generation_count("Transaction bảo đảm điều gì?")
    r=ask(client,"Transaction bảo đảm điều gì?")
    assert r.status_code == 200, r.text
    assert r.json()["status"] == "ANSWERED" and "atomicity" in r.json()["answer"]
    assert r.json()["model"] == "gpt-4.1-mini" and r.json()["promptVersion"] == "course-rag-v2"
    assert generation_count("Transaction bảo đảm điều gì?") == before+1
    assert r.json()["evidence"] == [{"chunkId":1,"quote":"Transaction bảo đảm tính nguyên tử (atomicity)."}]


def test_insufficient_evidence_is_normalized(client):
    r=ask(client,"[insufficient] Hỏi ngoài tài liệu")
    assert r.status_code == 200 and r.json()["answer"] == INSUFFICIENT_ANSWER
    assert r.json()["evidence"] == []


@pytest.mark.parametrize("marker", ["fake-id","fake-quote","missing-evidence","duplicate-evidence"])
def test_fabricated_citation_is_rejected_over_real_http(client, marker):
    assert ask(client,"["+marker+"] Question").status_code == 502


@pytest.mark.parametrize("marker,status",[("rate-limit",503),("unavailable",503),("html-error",503),
    ("timeout",504),("malformed",502),("truncated",502),("budget",422),("generation-rate-limit",503)])
def test_provider_failures_cross_real_http_without_leaking(client,marker,status):
    question="["+marker+"] Question"
    before=generation_count(question)
    r=ask(client,question)
    assert r.status_code == status,r.text
    assert "private" not in r.text and "simulation-key" not in r.text
    if marker=="budget": assert r.json()["detail"]["code"] == "TOKEN_BUDGET_EXCEEDED"
    assert generation_count(question) == before+(1 if marker in {"malformed","truncated","generation-rate-limit"} else 0)


def test_unreachable_provider_is_unavailable_or_connection_deadline():
    import socket
    with socket.socket() as socket_:
        socket_.bind(('127.0.0.1',0)); port=socket_.getsockname()[1]
    async def run():
        async with LlmProvider(LlmSettings(api_key="simulation-key",base_url=f"http://127.0.0.1:{port}/v1",timeout_seconds=1)) as provider:
            await provider.count_input_tokens([])
    with pytest.raises(ProviderError) as error: asyncio.run(run())
    # Windows may time out a refused connection instead of reporting ConnectError immediately.
    assert error.value.status_code in {503, 504}
