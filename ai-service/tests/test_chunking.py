"""Exercise the real Docling chunker offline; no OCR or model downloads."""
import pytest
from docling_core.transforms.chunker.tokenizer.base import BaseTokenizer
from docling_core.types.doc import (
    BoundingBox, DocItemLabel, DoclingDocument, ProvenanceItem, Size, TableCell, TableData,
)


class CharacterTokenizer(BaseTokenizer):
    limit: int = 160

    def count_tokens(self, text: str) -> int:
        return len(text)

    def get_max_tokens(self) -> int:
        return self.limit

    def get_tokenizer(self):
        return self.count_tokens


def document():
    doc = DoclingDocument(name="lecture")
    for page in (1, 2, 3):
        doc.add_page(page_no=page, size=Size(width=600, height=800))
    return doc


def provenance(page):
    return ProvenanceItem(page_no=page, bbox=BoundingBox(l=10, t=100, r=200, b=50), charspan=(0, 10))


def run(doc, maximum=160, overlap=24):
    from app.ingestion.chunking import ChunkingSettings, chunk_document
    return chunk_document(doc, 42, "bài-học.pdf", ChunkingSettings(
        tokenizer_model="offline-test", max_tokens=maximum, overlap_tokens=overlap,
    ), tokenizer=CharacterTokenizer(limit=maximum - overlap))


def test_heading_context_and_cross_page_item_provenance():
    doc = document()
    doc.add_heading(text="Cơ sở dữ liệu", level=1, prov=provenance(1))
    first = doc.add_text(label=DocItemLabel.TEXT, text="Khóa chính xác định một hàng.", prov=provenance(1))
    last = doc.add_text(label=DocItemLabel.TEXT, text="Khóa ngoại tham chiếu bảng khác.", prov=provenance(3))
    chunks = run(doc)
    assert len(chunks) == 1
    chunk = chunks[0]
    assert chunk.documentId == 42 and chunk.source == "bài-học.pdf"
    assert chunk.content == "Khóa chính xác định một hàng.\nKhóa ngoại tham chiếu bảng khác."
    assert chunk.headings == ["Cơ sở dữ liệu"]
    assert chunk.embeddingText.startswith("Cơ sở dữ liệu\n")
    assert (chunk.pageStart, chunk.pageEnd) == (1, 3)
    assert [r.pageNumber for r in chunk.sourceRefs] == [1, 3]
    assert chunk.blockIds == [first.self_ref, last.self_ref]
    assert chunk.sourceRefs[0].bbox.l == 10
    assert chunk.tokenCount == len(chunk.embeddingText) <= 160
    assert chunk.overlapContent == ""


def test_splits_long_vietnamese_content_with_bounded_overlap_and_no_loss():
    doc = document()
    doc.add_heading(text="Học tập", level=1, prov=provenance(1))
    text = " ".join(f"Câu{i} tiếng Việt." for i in range(40))
    doc.add_text(label=DocItemLabel.TEXT, text=text, prov=provenance(1))
    chunks = run(doc)
    assert len(chunks) > 2
    assert [c.chunkIndex for c in chunks] == list(range(len(chunks)))
    assert " ".join(" ".join(c.content for c in chunks).split()) == text
    assert all(0 < c.tokenCount <= 160 for c in chunks)
    assert all("\ufffd" not in c.embeddingText for c in chunks)
    for previous, current in zip(chunks, chunks[1:]):
        assert current.overlapContent
        assert previous.content.endswith(current.overlapContent)
        assert len(current.overlapContent) <= 24
        assert current.overlapContent in current.embeddingText
        assert current.overlapSourceRefs
    assert [c.model_dump() for c in chunks] == [c.model_dump() for c in run(doc)]


def test_does_not_overlap_across_different_headings():
    doc = document()
    doc.add_heading(text="Mục A", level=1, prov=provenance(1))
    doc.add_text(label=DocItemLabel.TEXT, text="Nội dung A.", prov=provenance(1))
    doc.add_heading(text="Mục B", level=1, prov=provenance(3))
    doc.add_text(label=DocItemLabel.TEXT, text="Nội dung B.", prov=provenance(3))
    chunks = run(doc)
    assert len(chunks) == 2
    assert chunks[1].overlapContent == ""
    assert [c.pageStart for c in chunks] == [1, 3]


def test_empty_pages_produce_no_chunks():
    assert run(document()) == []


def test_heading_only_page_remains_searchable_with_its_source():
    doc = document()
    heading = doc.add_heading(text="Lịch thi: thứ hai 08:00", level=1, prov=provenance(3))
    chunks = run(doc)
    assert len(chunks) == 1
    assert "Lịch thi: thứ hai 08:00" in chunks[0].content
    assert chunks[0].blockIds == [heading.self_ref]
    assert chunks[0].pageStart == 3


def test_trailing_heading_is_not_lost_after_body_content():
    doc = document()
    doc.add_heading(text="Mục một", level=1, prov=provenance(1))
    doc.add_text(label=DocItemLabel.TEXT, text="Bài học", prov=provenance(1))
    heading = doc.add_heading(text="Bài kiểm tra ngày mai", level=1, prov=provenance(3))
    chunks = run(doc)
    assert any("Bài kiểm tra ngày mai" in c.content and heading.self_ref in c.blockIds for c in chunks)


def test_headings_too_long_are_rejected_instead_of_silently_dropped():
    from app.ingestion.chunking import ChunkingError
    doc = document()
    doc.add_heading(text="Heading " * 100, level=1, prov=provenance(1))
    doc.add_text(label=DocItemLabel.TEXT, text="Body text.", prov=provenance(1))
    with pytest.raises(ChunkingError, match="heading"):
        run(doc)


def test_normalizes_vietnamese_unicode_for_embedding_and_content():
    doc = document()
    doc.add_heading(text="Tiếng Việt", level=1, prov=provenance(1))
    doc.add_text(label=DocItemLabel.TEXT, text="Nội dung", prov=provenance(1))
    chunk = run(doc)[0]
    assert chunk.content == "Nội dung"
    assert chunk.headings == ["Tiếng Việt"]
    assert "Tiếng Việt" in chunk.embeddingText


def test_missing_provenance_is_rejected_instead_of_inventing_page_one():
    from app.ingestion.chunking import ChunkingError
    doc = document()
    doc.add_text(label=DocItemLabel.TEXT, text="Không rõ trang")
    with pytest.raises(ChunkingError, match="provenance"):
        run(doc)


def test_overlap_can_be_disabled():
    doc = document()
    doc.add_text(label=DocItemLabel.TEXT, text="Một đoạn dài. " * 40, prov=provenance(1))
    chunks = run(doc, overlap=0)
    assert len(chunks) > 1
    assert all(c.overlapContent == "" and not c.overlapSourceRefs for c in chunks)


def test_table_chunks_keep_source_and_header():
    doc = document()
    cells = [TableCell(text=text, start_row_offset_idx=row, end_row_offset_idx=row + 1,
                      start_col_offset_idx=col, end_col_offset_idx=col + 1, column_header=row == 0)
             for row, col, text in [(0, 0, "Concept"), (0, 1, "Meaning")]
             + [(r, c, f"term{r}" if c == 0 else f"definition{r}") for r in range(1, 12) for c in range(2)]]
    table = doc.add_table(data=TableData(num_rows=12, num_cols=2, table_cells=cells), prov=provenance(3))
    chunks = run(doc, maximum=200)
    assert len(chunks) > 1
    assert all(c.blockIds == [table.self_ref] and c.pageStart == 3 for c in chunks)
    assert all("Concept" in c.content and "Meaning" in c.content for c in chunks)
    assert all(c.tokenCount <= 200 for c in chunks)
    assert all(c.overlapContent == "" for c in chunks)
    assert all(f"term{r}" in "\n".join(c.content for c in chunks) for r in range(1, 12))


def test_generated_picture_description_is_not_embedded_as_source_text():
    from docling_core.types.doc.items.picture.meta import PictureMeta
    from docling_core.types.doc.common.meta import DescriptionMetaField
    doc = document()
    picture = doc.add_picture(prov=provenance(1))
    picture.meta = PictureMeta(description=DescriptionMetaField(text="INVENTED DESCRIPTION", created_by="vlm"))
    doc.add_text(label=DocItemLabel.TEXT, text="OCR trong hình", parent=picture, prov=provenance(1))
    caption = doc.add_text(label=DocItemLabel.CAPTION, text="Hình 1: sơ đồ", prov=provenance(1))
    picture.captions.append(caption.get_ref())
    doc.add_text(label=DocItemLabel.TEXT, text="Nội dung thật", prov=provenance(1))
    chunks = run(doc)
    assert chunks
    assert all("INVENTED DESCRIPTION" not in c.embeddingText for c in chunks)
    content = "\n".join(c.content for c in chunks)
    assert content.count("OCR trong hình") == 1
    assert content.count("Hình 1: sơ đồ") == 1
    assert "Nội dung thật" in content


@pytest.mark.parametrize("maximum,overlap", [(0, 0), (20, -1), (20, 20), (20, 21)])
def test_invalid_limits_are_rejected(maximum, overlap):
    from app.ingestion.chunking import ChunkingSettings
    with pytest.raises(ValueError):
        ChunkingSettings(max_tokens=maximum, overlap_tokens=overlap)


def test_unbroken_text_obeys_token_limit():
    doc = document()
    doc.add_text(label=DocItemLabel.TEXT, text="abc" * 300, prov=provenance(1))
    chunks = run(doc)
    assert "".join(c.content for c in chunks) == "abc" * 300
    assert all(c.tokenCount <= 160 for c in chunks)


def test_extraction_opt_in_chunks_native_document(monkeypatch):
    from types import SimpleNamespace
    from app.ingestion import chunking
    from app.ingestion.docling_parser import DoclingExtractor
    from app.ingestion.settings import ExtractionSettings
    from docling.datamodel.base_models import ConversionStatus
    doc = document()
    doc.add_heading(text="Học tập", level=1, prov=provenance(1))
    doc.add_text(label=DocItemLabel.TEXT, text="Nội dung gốc.", prov=provenance(3))
    monkeypatch.setattr(chunking, "load_tokenizer", lambda settings: (
        CharacterTokenizer(limit=settings.max_tokens - settings.overlap_tokens), 0,
    ), raising=False)
    extractor = DoclingExtractor(ExtractionSettings.from_env())
    extractor._converter = SimpleNamespace(convert=lambda *args, **kwargs: SimpleNamespace(
        status=ConversionStatus.SUCCESS, document=doc, has_timeout_errors=lambda: False,
    ))
    result = extractor._convert(b"%PDF-", 42, "lesson.pdf", include_chunks=True)
    assert result.chunks[0].headings == ["Học tập"]
    assert result.chunks[0].pageStart == 3
    assert result.chunks[0].content == "Nội dung gốc."
    assert result.pageCount == 3


def test_extraction_only_never_loads_tokenizer(monkeypatch):
    from types import SimpleNamespace
    from app.ingestion import chunking
    from app.ingestion.docling_parser import DoclingExtractor
    from app.ingestion.settings import ExtractionSettings
    from docling.datamodel.base_models import ConversionStatus
    monkeypatch.setattr(chunking, "load_tokenizer", lambda settings: pytest.fail("Tokenizer loaded"), raising=False)
    extractor = DoclingExtractor(ExtractionSettings.from_env())
    extractor._converter = SimpleNamespace(convert=lambda *args, **kwargs: SimpleNamespace(
        status=ConversionStatus.SUCCESS, document=document(), has_timeout_errors=lambda: False,
    ))
    assert extractor._convert(b"%PDF-", 42, "lesson.pdf").chunks is None
