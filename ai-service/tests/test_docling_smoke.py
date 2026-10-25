"""Opt-in real model smoke tests. First run downloads public model weights."""
import os
from io import BytesIO

import pytest

pytestmark = pytest.mark.skipif(
    os.getenv("RUN_DOCLING_TESTS") != "1", reason="Set RUN_DOCLING_TESTS=1 to run local models",
)


def test_real_docling_ocr_reads_scanned_pdf(monkeypatch):
    from PIL import Image, ImageDraw, ImageFont
    from fastapi.testclient import TestClient
    from app.main import app
    from app.ingestion.schemas import ExtractionResponse

    font_path = os.getenv("TEST_FONT_PATH", "C:/Windows/Fonts/arial.ttf" if os.name == "nt" else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    image = Image.new("RGB", (1200, 1600), "white")
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(font_path, 44)
    draw.text((80, 120), "StudyHub OCR pipeline", font=font, fill="black")
    draw.text((80, 240), "Tài liệu học tập tiếng Việt", font=font, fill="black")
    output = BytesIO()
    image.save(output, format="PDF", resolution=144)
    monkeypatch.setenv("AI_SERVICE_TOKEN", "smoke-only-token")
    with TestClient(app) as client:
        response = client.post(
            "/internal/documents/extract", headers={"X-AI-Service-Token": "smoke-only-token"},
            data={"documentId": "42", "includeChunks": "true"},
            files={"file": ("../../scan.pdf", output.getvalue(), "application/pdf")},
        )
    assert response.status_code == 200, response.text
    result = ExtractionResponse.model_validate(response.json())
    assert result.source == "scan.pdf"
    assert result.pageCount == 1
    assert "StudyHub" in result.pages[0].text
    assert "tiếng Việt" in result.pages[0].text
    assert result.pages[0].blocks[0].bbox.r > 0
    assert result.chunks
    assert any("tiếng Việt" in chunk.content for chunk in result.chunks)
    assert all(chunk.documentId == 42 and chunk.source == "scan.pdf" for chunk in result.chunks)
    assert all(chunk.sourceRefs and chunk.pageStart == chunk.pageEnd == 1 for chunk in result.chunks)
    assert all(chunk.tokenCount <= 384 for chunk in result.chunks)


@pytest.mark.skipif(os.getenv("RUN_DOCLING_VLM_TESTS") != "1", reason="Set RUN_DOCLING_VLM_TESTS=1 for local VLM")
def test_real_docling_describes_diagram_separately_from_ocr():
    from dataclasses import replace
    from PIL import Image, ImageDraw, ImageFont
    from app.ingestion.docling_parser import DoclingExtractor
    from app.ingestion.settings import ExtractionSettings

    font_path = os.getenv("TEST_FONT_PATH", "C:/Windows/Fonts/arial.ttf" if os.name == "nt" else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    image = Image.new("RGB", (1400, 1800), "white")
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(font_path, 38)
    draw.text((70, 70), "System architecture", font=font, fill="black")
    for x, label in [(70, "Client"), (520, "Backend"), (1000, "Database")]:
        draw.rounded_rectangle((x, 600, x + 300, 900), radius=25, outline="navy", width=10, fill="#ddf0ff")
        draw.text((x + 30, 700), label, font=font, fill="black")
    for start, end in [(370, 520), (820, 1000)]:
        draw.line((start, 750, end - 15, 750), fill="black", width=12)
        draw.polygon([(end, 750), (end - 30, 730), (end - 30, 770)], fill="black")
    output = BytesIO()
    image.save(output, format="PDF", resolution=144)
    settings = replace(ExtractionSettings.from_env(), picture_descriptions=True)
    result = DoclingExtractor(settings).extract(output.getvalue(), 42, "diagram.pdf")
    figures = [block for page in result.pages for block in page.blocks if block.type == "picture"]
    assert figures and any(figure.description for figure in figures)
    assert all(figure.descriptionMethod == "vision_model" for figure in figures if figure.description)
    for figure in figures:
        if figure.description:
            assert figure.description not in result.pages[0].text
