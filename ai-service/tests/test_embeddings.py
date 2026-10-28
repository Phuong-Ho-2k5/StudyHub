import math
import time

import pytest
from fastapi.testclient import TestClient

from app.main import app
from embedding_workers import echo_worker as _echo_worker, delayed_reader as _delayed_reader


def test_embedding_route_authenticates_before_reading_json(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_TOKEN", "embedding-test")
    with TestClient(app) as client:
        response = client.post("/internal/embeddings", content="not json")
    assert response.status_code == 401


def test_embedding_route_requires_configuration(monkeypatch):
    monkeypatch.delenv("AI_SERVICE_TOKEN", raising=False)
    with TestClient(app) as client:
        assert client.post("/internal/embeddings", json={"texts": ["test"]}).status_code == 503


@pytest.mark.parametrize("texts", [[], [" "], ["x"] * 33, ["x" * 16385]])
def test_embedding_route_rejects_invalid_batch_before_model_load(monkeypatch, texts):
    monkeypatch.setenv("AI_SERVICE_TOKEN", "embedding-test")
    with TestClient(app) as client:
        response = client.post("/internal/embeddings", headers={"X-AI-Service-Token": "embedding-test"}, json={"texts": texts})
    assert response.status_code == 422


def test_embedding_route_returns_vectors_in_input_order(monkeypatch):
    from app.embedding import api

    class Service:
        def __init__(self, settings):
            self.settings = settings

        def close(self):
            pass

        def embed(self, texts, input_type="passage"):
            assert texts == ["Học toán", "Học lý"]
            return {"model": "test@revision", "dimensions": 2, "vectors": [[1., 0.], [0., 1.]]}

    monkeypatch.setenv("AI_SERVICE_TOKEN", "embedding-test")
    monkeypatch.setattr(api, "EmbeddingService", Service)
    with TestClient(app) as client:
        response = client.post("/internal/embeddings", headers={"X-AI-Service-Token": "embedding-test"}, json={"texts": ["Học toán", "Học lý"]})
    assert response.status_code == 200
    assert response.json()["vectors"] == [[1., 0.], [0., 1.]]


def test_encoder_uses_passage_prefix_masked_pooling_and_normalization():
    import torch
    from app.embedding.encoder import E5Encoder
    from app.embedding.settings import EmbeddingSettings

    class Tokenizer:
        def __call__(self, texts, **kwargs):
            assert texts == ["passage: Học toán", "passage: Học lý"]
            assert kwargs.get("truncation") is False
            ids = [[1, 2], [1]]
            if kwargs.get("return_tensors"):
                return {"input_ids": torch.tensor([[1, 2], [1, 0]]), "attention_mask": torch.tensor([[1, 1], [1, 0]])}
            return {"input_ids": ids}

    class Model:
        def __call__(self, **kwargs):
            from types import SimpleNamespace
            return SimpleNamespace(last_hidden_state=torch.tensor([[[3., 0.], [0., 4.]], [[0., 2.], [99., 99.]]]))

    encoder = E5Encoder(EmbeddingSettings(dimensions=2))
    encoder.tokenizer, encoder.model = Tokenizer(), Model()
    result = encoder.embed(["Học toán", "Học lý"])
    assert result[0] == pytest.approx([0.6, 0.8])
    assert result[1] == pytest.approx([0., 1.])


def test_encoder_refuses_overflow_including_prefix():
    from app.embedding.encoder import E5Encoder, EmbeddingInputError
    from app.embedding.settings import EmbeddingSettings

    class Tokenizer:
        def __call__(self, texts, **kwargs):
            assert texts == ["passage: Nội dung"]
            assert kwargs.get("truncation") is False
            return {"input_ids": [[1] * 513]}

    encoder = E5Encoder(EmbeddingSettings())
    encoder.tokenizer, encoder.model = Tokenizer(), object()
    with pytest.raises(EmbeddingInputError):
        encoder.embed(["Nội dung"])


@pytest.mark.parametrize("vectors", [[[1., float("nan")]], [[0., 0.]], [[1.]], []])
def test_rejects_corrupt_model_output(vectors):
    from app.embedding.encoder import validate_vectors
    with pytest.raises(ValueError):
        validate_vectors(vectors, count=1, dimensions=2)


def test_worker_reuses_process_then_recovers_after_hard_timeout(monkeypatch):
    from fastapi import HTTPException
    from app.embedding import service as module
    from app.embedding.settings import EmbeddingSettings

    monkeypatch.setattr(module, "_embedding_worker", _echo_worker)
    service = module.EmbeddingService(EmbeddingSettings(timeout_seconds=3))
    try:
        assert service.embed(["one"])["vectors"][0][0] == 1.
        worker = service._worker
        assert service.embed(["two"])["vectors"][0][0] == 1.
        assert service._worker is worker
        with pytest.raises(HTTPException) as error:
            service.embed(["hang"])
        assert error.value.status_code == 504
        from multiprocessing import active_children
        assert worker not in active_children()
        assert service.embed(["three"], input_type="query")["vectors"][0][0] == 1.
        assert service._worker is not worker
    finally:
        service.close()


def test_deadline_includes_large_request_ipc_and_worker_startup(monkeypatch):
    from fastapi import HTTPException
    from app.embedding import service as module
    from app.embedding.settings import EmbeddingSettings
    monkeypatch.setattr(module, "_embedding_worker", _delayed_reader)
    service = module.EmbeddingService(EmbeddingSettings(timeout_seconds=1))
    started = time.monotonic()
    try:
        with pytest.raises(HTTPException) as error:
            service.embed(["x" * 16384] * 32)
        assert error.value.status_code == 504
        assert time.monotonic() - started < 3
    finally:
        service.close()


def test_failed_process_start_does_not_poison_next_request(monkeypatch):
    from fastapi import HTTPException
    from multiprocessing.process import BaseProcess
    from app.embedding import service as module
    from app.embedding.settings import EmbeddingSettings

    monkeypatch.setattr(module, "_embedding_worker", _echo_worker)
    service = module.EmbeddingService(EmbeddingSettings(timeout_seconds=3))
    original_start = BaseProcess.start

    def failed_start(self):
        raise OSError("Temporary process resource exhaustion")

    try:
        monkeypatch.setattr(BaseProcess, "start", failed_start)
        with pytest.raises(HTTPException) as error:
            service.embed(["one"])
        assert error.value.status_code == 503
        monkeypatch.setattr(BaseProcess, "start", original_start)
        assert service.embed(["two"])["vectors"][0][0] == 1.
    finally:
        service.close()


def test_busy_worker_fails_fast():
    from fastapi import HTTPException
    from app.embedding.service import EmbeddingService
    from app.embedding.settings import EmbeddingSettings
    service = EmbeddingService(EmbeddingSettings())
    service._lock.acquire()
    try:
        with pytest.raises(HTTPException) as error:
            service.embed(["test"])
        assert error.value.status_code == 503
    finally:
        service._lock.release()
        service.close()


def test_concurrent_first_requests_share_one_model_service(monkeypatch):
    from concurrent.futures import ThreadPoolExecutor
    from app.embedding import api
    from app.embedding.settings import EmbeddingSettings

    created = []

    class Service:
        def __init__(self, settings):
            time.sleep(0.05)
            self.settings = settings
            created.append(self)

        def close(self):
            pass

    api.close_embedding_service()
    monkeypatch.setattr(api, "EmbeddingService", Service)
    try:
        with ThreadPoolExecutor(max_workers=8) as pool:
            services = list(pool.map(lambda _: api.get_embedding_service(EmbeddingSettings()), range(8)))
        assert len(created) == 1
        assert all(item is services[0] for item in services)
    finally:
        api.close_embedding_service()


def test_model_smoke():
    import os
    if os.getenv("RUN_EMBEDDING_TESTS") != "1":
        pytest.skip("Set RUN_EMBEDDING_TESTS=1 to download and run the real model")
    from app.embedding.encoder import E5Encoder
    from app.embedding.settings import EmbeddingSettings
    result = E5Encoder(EmbeddingSettings()).embed(["Hàm số bậc hai", "Định luật Newton"])
    assert len(result) == 2
    assert len(result[0]) == 384
    assert result[0] != result[1]
    assert sum(x * x for x in result[0]) == pytest.approx(1., abs=1e-5)
    assert all(math.isfinite(x) for row in result for x in row)


def test_real_embedding_endpoint_smoke(monkeypatch):
    import os
    if os.getenv("RUN_EMBEDDING_TESTS") != "1":
        pytest.skip("Set RUN_EMBEDDING_TESTS=1 to run the real worker and model")
    monkeypatch.setenv("AI_SERVICE_TOKEN", "embedding-smoke")
    with TestClient(app) as client:
        response = client.post("/internal/embeddings", headers={"X-AI-Service-Token": "embedding-smoke"},
                               json={"texts": ["Hàm số bậc hai", "Định luật Newton"]})
    assert response.status_code == 200, response.text
    data = response.json()
    assert data["dimensions"] == 384
    assert data["model"] == "intfloat/multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3"
    assert len(data["vectors"]) == 2
    assert len(data["vectors"][0]) == 384
    assert data["vectors"][0] != data["vectors"][1]
