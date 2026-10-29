import asyncio
import pytest
from pydantic import ValidationError
from app.generation.schemas import Evidence, GenerationResult, RagAnswerRequest
from app.generation.citations import validate_evidence
from app.generation.provider import ProviderError
from app.generation.service import RagService
from app.generation.settings import LlmSettings


def contexts():
    return RagAnswerRequest(question="Question", contexts=[dict(chunkId=i, documentId=7,
        documentTitle="Database", chunkIndex=i-1, content="  Tiếng Việt 😀 atomicity.  ") for i in (1, 2)]).contexts


def result(evidence, status="ANSWERED"):
    return GenerationResult(status=status, answer="Atomicity", model="model", evidence=evidence)


def test_exact_unicode_quote_and_insufficient_context():
    evidence=[Evidence(chunkId=1, quote=" Tiếng Việt 😀 ")]
    assert validate_evidence(result(evidence), contexts()) == evidence
    assert validate_evidence(result([], "INSUFFICIENT_CONTEXT"), contexts()) == []


@pytest.mark.parametrize("evidence,status", [([], "ANSWERED"),
    ([dict(chunkId=9,quote="atomicity")],"ANSWERED"),
    ([dict(chunkId=1,quote="invented")],"ANSWERED"),
    ([dict(chunkId=1,quote="atomicity")]*2,"ANSWERED"),
    ([dict(chunkId=1,quote="atomicity")],"INSUFFICIENT_CONTEXT")])
def test_invalid_evidence_is_gateway_error(evidence, status):
    with pytest.raises(ProviderError) as error:
        validate_evidence(result(evidence,status), contexts())
    assert error.value.status_code == 502


@pytest.mark.parametrize("chunk_id,quote", [("1","x"),(1.0,"x"),(True,"x"),(0,"x"),
    (1,""),(1," "),(1,"\u00a0"),(1,"\u202f"),(1,"\u0085"),(1,"x"*1001),(1,None)])
def test_strict_evidence_schema(chunk_id, quote):
    with pytest.raises(ValidationError): Evidence(chunkId=chunk_id,quote=quote)


def test_too_many_evidence_items_rejected():
    with pytest.raises(ValidationError): result([dict(chunkId=i,quote="x") for i in range(1,22)])


def test_service_rejects_evidence_from_trimmed_context(monkeypatch):
    class Provider:
        def __init__(self, settings): pass
        async def __aenter__(self): return self
        async def __aexit__(self,*args): pass
        async def count_input_tokens(self, messages):
            import json
            return 201 if len(json.loads(messages[1]["content"])["contexts"]) == 2 else 100
        async def generate(self,*args): return result([dict(chunkId=2,quote="atomicity")])
    monkeypatch.setattr("app.generation.service.LlmProvider",Provider)
    with pytest.raises(ProviderError) as error:
        asyncio.run(RagService(LlmSettings(api_key="test",max_input_tokens=200)).answer(
            RagAnswerRequest(question="Question",contexts=contexts())))
    assert error.value.status_code == 502
