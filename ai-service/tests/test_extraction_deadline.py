import time
from dataclasses import replace
from types import SimpleNamespace

import pytest
from fastapi import HTTPException


def slow_worker(settings, payload, document_id, source, connection, include_chunks=False):
    time.sleep(10)


@pytest.mark.parametrize("include_chunks", [False, True])
def test_overall_deadline_includes_slow_model_work(monkeypatch, include_chunks):
    from app.ingestion import docling_parser
    from app.ingestion.settings import ExtractionSettings
    from docling_core.types.doc import DoclingDocument, Size
    from docling.datamodel.base_models import ConversionStatus

    doc = DoclingDocument(name="slow")
    doc.add_page(page_no=1, size=Size(width=600, height=800))

    class SlowConverter:
        def convert(self, *args, **kwargs):
            time.sleep(2)
            return SimpleNamespace(status=ConversionStatus.SUCCESS, document=doc, has_timeout_errors=lambda: False)

    monkeypatch.setattr(docling_parser, "_extract_in_worker", slow_worker, raising=False)
    extractor = docling_parser.DoclingExtractor(replace(ExtractionSettings.from_env(), timeout_seconds=1))
    monkeypatch.setattr(extractor, "_load_converter", lambda: SlowConverter())
    started = time.monotonic()
    with pytest.raises(HTTPException) as error:
        extractor.extract(b"%PDF-", 42, "slow.pdf", include_chunks=include_chunks)
    assert error.value.status_code == 504
    assert time.monotonic() - started < 5
