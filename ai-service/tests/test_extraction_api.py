import pytest
from io import BytesIO
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_TOKEN", "test-internal-token")
    monkeypatch.setenv("AI_MAX_FILE_BYTES", "1024")
    with TestClient(app) as client:
        yield client


def test_extraction_requires_internal_token_before_parsing(client):
    response = client.post("/internal/documents/extract")
    assert response.status_code == 401


def test_extraction_fails_closed_without_service_token(client, monkeypatch):
    monkeypatch.delenv("AI_SERVICE_TOKEN")
    response = client.post("/internal/documents/extract")
    assert response.status_code == 503


def test_rejects_non_pdf_without_loading_models(client):
    response = client.post(
        "/internal/documents/extract",
        headers={"X-AI-Service-Token": "test-internal-token"},
        data={"documentId": "42"},
        files={"file": ("lecture.pdf", b"not a pdf", "application/pdf")},
    )
    assert response.status_code == 415


def test_rejects_oversized_pdf_before_loading_models(client):
    response = client.post(
        "/internal/documents/extract",
        headers={"X-AI-Service-Token": "test-internal-token"},
        data={"documentId": "42"},
        files={"file": ("lecture.pdf", b"%PDF-" + b"x" * 1024, "application/pdf")},
    )
    assert response.status_code == 413


def test_rejects_corrupt_pdf_even_with_valid_header(client):
    response = client.post(
        "/internal/documents/extract",
        headers={"X-AI-Service-Token": "test-internal-token"},
        data={"documentId": "42"},
        files={"file": ("lecture.pdf", b"%PDF-1.7\ninvalid", "application/pdf")},
    )
    assert response.status_code == 415


def test_rejects_page_limit_before_loading_models(client, monkeypatch):
    import pypdfium2 as pdfium

    monkeypatch.setenv("AI_MAX_PAGES", "1")
    monkeypatch.setenv("AI_MAX_FILE_BYTES", "100000")
    with pdfium.PdfDocument.new() as pdf:
        for _ in range(2):
            pdf.new_page(600, 800).close()
        output = BytesIO()
        pdf.save(output)
    response = client.post(
        "/internal/documents/extract",
        headers={"X-AI-Service-Token": "test-internal-token"},
        data={"documentId": "42"},
        files={"file": ("lecture.pdf", output.getvalue(), "application/pdf")},
    )
    assert response.status_code == 413


def test_bounds_chunked_request_without_content_length(client):
    response = client.post(
        "/internal/documents/extract",
        headers={
            "X-AI-Service-Token": "test-internal-token",
            "Content-Type": "multipart/form-data; boundary=oversized",
        },
        content=iter([b"--oversized\r\n" + b"x" * (2 * 1024 * 1024)]),
    )
    assert response.status_code == 413
