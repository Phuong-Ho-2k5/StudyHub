from docling_core.types.doc import (
    BoundingBox, DocItemLabel, DoclingDocument, ProvenanceItem, Size, TableCell, TableData,
)
from docling_core.types.doc.items.picture.meta import PictureMeta
from docling_core.types.doc.common.meta import DescriptionMetaField


def provenance(page):
    return ProvenanceItem(
        page_no=page,
        bbox=BoundingBox(l=10, t=20, r=200, b=80),
        charspan=(0, 50),
    )


def test_preserves_vietnamese_paragraphs_and_original_page_numbers():
    from app.ingestion.docling_parser import map_document

    doc = DoclingDocument(name="lecture")
    for page in [1, 2, 3]:
        doc.add_page(page_no=page, size=Size(width=600, height=800))
    doc.add_text(label=DocItemLabel.TEXT, text="  Tiếng Việt  \r\n\r\n\r\nĐoạn hai.\x00", prov=provenance(1))
    doc.add_text(label=DocItemLabel.TEXT, text="Trang cuối", prov=provenance(3))

    result = map_document(doc, document_id=42, source="lecture.pdf")
    assert result.documentId == 42
    assert result.pageCount == 3
    assert [p.pageNumber for p in result.pages] == [1, 2, 3]
    assert result.pages[0].text == "Tiếng Việt\n\nĐoạn hai."
    assert result.pages[1].text == ""
    assert result.pages[2].text == "Trang cuối"
    assert "PAGE_2_NO_TEXT" in result.warnings
    assert result.pages[0].blocks[0].bbox.l == 10


def test_keeps_figure_ocr_and_generated_description_distinct():
    from app.ingestion.docling_parser import map_document

    doc = DoclingDocument(name="diagram")
    doc.add_page(page_no=1, size=Size(width=600, height=800))
    picture = doc.add_picture(prov=provenance(1))
    picture.meta = PictureMeta(description=DescriptionMetaField(text="Client calls Backend.", created_by="local-vlm"))
    doc.add_text(label=DocItemLabel.TEXT, text="Client Backend", parent=picture, prov=provenance(1))
    result = map_document(doc, document_id=7, source="diagram.pdf")
    figure = next(b for b in result.pages[0].blocks if b.type == "picture")
    assert figure.description == "Client calls Backend."
    assert figure.descriptionMethod == "vision_model"
    assert figure.text == "Client Backend"
    assert figure.id == picture.self_ref
    # Generated descriptions must not masquerade as extracted page text.
    assert "Client calls Backend." not in result.pages[0].text
    assert result.pages[0].text.count("Client Backend") == 1


def test_warns_when_picture_has_not_been_interpreted():
    from app.ingestion.docling_parser import map_document

    doc = DoclingDocument(name="diagram")
    doc.add_page(page_no=1, size=Size(width=600, height=800))
    doc.add_picture(prov=provenance(1))
    result = map_document(doc, document_id=7, source="diagram.pdf")
    assert "PAGE_1_PICTURE_DESCRIPTION_UNAVAILABLE" in result.warnings


def test_table_remains_readable_with_page_provenance():
    from app.ingestion.docling_parser import map_document

    doc = DoclingDocument(name="table")
    doc.add_page(page_no=1, size=Size(width=600, height=800))
    cells = [
        TableCell(text=text, start_row_offset_idx=row, end_row_offset_idx=row + 1,
                  start_col_offset_idx=col, end_col_offset_idx=col + 1, column_header=row == 0)
        for row, col, text in [(0, 0, "Concept"), (0, 1, "Meaning"), (1, 0, "RAG"), (1, 1, "Retrieval")]
    ]
    doc.add_table(data=TableData(num_rows=2, num_cols=2, table_cells=cells), prov=provenance(1))
    result = map_document(doc, document_id=42, source="table.pdf")
    block = result.pages[0].blocks[0]
    assert block.type == "table"
    assert "|" in block.text and "RAG" in block.text and "Retrieval" in block.text
    assert result.pages[0].pageNumber == 1
