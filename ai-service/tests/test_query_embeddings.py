"""Catch passage/query confusion across API, IPC, and the real encoder adapter."""
import math
import os

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.mark.parametrize("input_type,prefix", [("query", "query: "), ("passage", "passage: ")])
@pytest.mark.parametrize("token_count", [512, 513])
def test_encoder_selects_prefix_before_token_budget_check(input_type, prefix, token_count):
    import torch
    from types import SimpleNamespace
    from app.embedding.encoder import E5Encoder, EmbeddingInputError
    from app.embedding.settings import EmbeddingSettings

    class Tokenizer:
        def __call__(self, texts, **kwargs):
            assert texts == [prefix + "Transaction"]
            assert kwargs.get("truncation") is False
            if kwargs.get("return_tensors"):
                return {"input_ids": torch.tensor([[1]]), "attention_mask": torch.tensor([[1]])}
            assert kwargs.get("add_special_tokens") is True
            return {"input_ids": [[1] * token_count]}

    class Model:
        def __call__(self, **kwargs):
            return SimpleNamespace(last_hidden_state=torch.tensor([[[3., 4.]]]))

    encoder = E5Encoder(EmbeddingSettings(dimensions=2))
    encoder.tokenizer, encoder.model = Tokenizer(), Model()
    if token_count > 512:
        with pytest.raises(EmbeddingInputError):
            encoder.embed(["Transaction"], input_type=input_type)
    else:
        assert encoder.embed(["Transaction"], input_type=input_type)[0] == pytest.approx([0.6, 0.8])


@pytest.mark.parametrize("input_type", ["other", "", None, 1])
def test_route_rejects_invalid_input_type_before_loading_model(monkeypatch, input_type):
    from app.embedding import api
    class Service:
        def embed(self, texts):
            return {"model": "test", "dimensions": 2, "vectors": [[1., 0.]]}
    monkeypatch.setattr(api, "get_embedding_service", lambda settings: Service())
    monkeypatch.setenv("AI_SERVICE_TOKEN", "query-test")
    with TestClient(app) as client:
        response = client.post("/internal/embeddings", headers={"X-AI-Service-Token": "query-test"},
                               json={"texts": ["Transaction"], "inputType": input_type})
    assert response.status_code == 422


@pytest.mark.parametrize("input_type", ["query", "passage", None])
def test_route_selects_query_or_default_passage(monkeypatch, input_type):
    from app.embedding import api

    class Service:
        def embed(self, texts, input_type="passage"):
            value = 1. if input_type == "query" else -1.
            return {"model": "test@revision", "dimensions": 2, "vectors": [[value, 0.] for _ in texts]}

    monkeypatch.setenv("AI_SERVICE_TOKEN", "query-test")
    monkeypatch.setattr(api, "get_embedding_service", lambda settings: Service())
    body = {"texts": ["Transaction"]}
    if input_type is not None:
        body["inputType"] = input_type
    with TestClient(app) as client:
        response = client.post("/internal/embeddings", headers={"X-AI-Service-Token": "query-test"}, json=body)
    assert response.status_code == 200
    assert response.json()["vectors"] == ([[1., 0.]] if input_type == "query" else [[-1., 0.]])


def _typed_worker(settings, connection):
    try:
        while True:
            texts, input_type = connection.recv()
            value = 1. if input_type == "query" else -1.
            connection.send((200, [[value] + [0.] * (settings.dimensions - 1) for _ in texts]))
    except EOFError:
        pass
    finally:
        connection.close()


def test_service_preserves_type_across_interleaved_requests(monkeypatch):
    from app.embedding import service as module
    from app.embedding.settings import EmbeddingSettings
    monkeypatch.setattr(module, "_embedding_worker", _typed_worker)
    service = module.EmbeddingService(EmbeddingSettings(timeout_seconds=5))
    try:
        assert service.embed(["Question"], input_type="query")["vectors"][0][0] == 1.
        assert service.embed(["Document"])["vectors"][0][0] == -1.
        assert service.embed(["Question"], input_type="query")["vectors"][0][0] == 1.
    finally:
        service.close()


def test_production_worker_forwards_type_to_encoder(monkeypatch):
    from app.embedding import service as module
    from app.embedding.settings import EmbeddingSettings

    class Encoder:
        def __init__(self, settings):
            pass

        def embed(self, texts, input_type="passage"):
            return [[1. if input_type == "query" else -1., 0.] for _ in texts]

    class Connection:
        def __init__(self):
            self.requests = iter([(["Q"], "query"), (["D"], "passage")])
            self.responses = []

        def recv(self):
            try:
                return next(self.requests)
            except StopIteration:
                raise EOFError

        def send(self, value):
            self.responses.append(value)

        def close(self):
            pass

    monkeypatch.setattr(module, "E5Encoder", Encoder)
    connection = Connection()
    module._embedding_worker(EmbeddingSettings(dimensions=2), connection)
    assert connection.responses == [(200, [[1., 0.]]), (200, [[-1., 0.]])]


@pytest.mark.skipif(os.getenv("RUN_EMBEDDING_TESTS") != "1", reason="Real model tests are opt-in")
def test_real_query_and_passage_embeddings_are_compatible(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_TOKEN", "query-smoke")
    with TestClient(app) as client:
        query = client.post("/internal/embeddings", headers={"X-AI-Service-Token": "query-smoke"},
                            json={"texts": ["Transaction"], "inputType": "query"})
        passage = client.post("/internal/embeddings", headers={"X-AI-Service-Token": "query-smoke"},
                              json={"texts": ["Transaction"]})
    assert query.status_code == passage.status_code == 200
    q, p = query.json(), passage.json()
    assert q["model"] == p["model"]
    assert q["dimensions"] == p["dimensions"] == 384
    assert q["vectors"][0] != p["vectors"][0]
    for vector in (q["vectors"][0], p["vectors"][0]):
        assert len(vector) == 384
        assert all(math.isfinite(v) for v in vector)
        assert sum(v * v for v in vector) == pytest.approx(1., abs=1e-5)
