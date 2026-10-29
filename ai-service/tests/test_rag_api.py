from fastapi.testclient import TestClient
import pytest
from app.main import app
from app.generation import api
from app.generation.schemas import RagAnswerResponse, INSUFFICIENT_ANSWER


def payload():
    return {"question":"  Transaction?  ","contexts":[{"chunkId":1,"documentId":2,"documentTitle":"Database",
        "chunkIndex":0,"content":"Atomicity","pageStart":1,"pageEnd":2}]}


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_TOKEN", "test-token")
    monkeypatch.setenv("LLM_API_KEY", "test-key")
    with TestClient(app) as client: yield client


@pytest.mark.parametrize("token", [None,"wrong"])
def test_internal_auth_precedes_generation(client, monkeypatch, token):
    def fail(*args): raise AssertionError("must not generate")
    monkeypatch.setattr(api, "RagService", fail)
    response=client.post("/internal/rag/answer",json=payload(),headers={} if token is None else {"X-AI-Service-Token":token})
    assert response.status_code == 401


@pytest.mark.parametrize("field,value", [("question"," "),("question","x"*16385),("contexts",[]),
    ("contexts",[{"chunkId":-1}]),("contexts",None)])
def test_invalid_question_or_context_does_not_generate(client, monkeypatch, field, value):
    def fail(*args): raise AssertionError("must not generate")
    monkeypatch.setattr(api,"RagService",fail)
    body=payload(); body[field]=value
    assert client.post("/internal/rag/answer",json=body,headers={"X-AI-Service-Token":"test-token"}).status_code == 422


def test_answer_endpoint_normalizes_question(client, monkeypatch):
    class FakeService:
        def __init__(self, settings): pass
        async def answer(self, request):
            assert request.question == "Transaction?"
            return RagAnswerResponse(status="ANSWERED",answer="Atomicity",evidence=[dict(chunkId=1,quote="Atomicity")],model="gpt-4.1-mini",promptVersion="course-rag-v2")
    monkeypatch.setattr(api,"RagService",FakeService)
    r=client.post("/internal/rag/answer",json=payload(),headers={"X-AI-Service-Token":"test-token"})
    assert r.status_code == 200 and r.json()["answer"] == "Atomicity"


def test_no_key_keeps_health_available_but_chat_unavailable(client,monkeypatch):
    monkeypatch.delenv("LLM_API_KEY",raising=False)
    assert client.get("/health").status_code == 200
    r=client.post("/internal/rag/answer",json=payload(),headers={"X-AI-Service-Token":"test-token"})
    assert r.status_code == 503 and "test-key" not in r.text


def test_page_pair_and_extra_provider_override_are_rejected(client):
    for body in [dict(payload(),model="attacker-model"),payload()]:
        if "model" not in body: body["contexts"][0]["pageEnd"]=None
        assert client.post("/internal/rag/answer",json=body,headers={"X-AI-Service-Token":"test-token"}).status_code == 422


def test_body_size_guard_rejects_before_generation(client,monkeypatch):
    def fail(*args): raise AssertionError("must not generate")
    monkeypatch.setattr(api,"RagService",fail)
    r=client.post("/internal/rag/answer",json=payload(),headers={
        "X-AI-Service-Token":"test-token","Content-Length":str(8*1024*1024+1)})
    assert r.status_code == 413
