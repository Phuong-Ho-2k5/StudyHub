import re
import unicodedata
import multiprocessing
from io import BytesIO
from pathlib import Path
from threading import Lock

from fastapi import HTTPException

from .schemas import BoundingBox, ContentBlock, ExtractedPage, ExtractionResponse
from .settings import ExtractionSettings


def clean_text(text: str) -> str:
    text = unicodedata.normalize("NFC", text).replace("\r\n", "\n").replace("\r", "\n")
    text = "".join(c for c in text if c in "\n\t" or unicodedata.category(c) != "Cc")
    lines = [re.sub(r"[^\S\n]+", " ", line).strip() for line in text.split("\n")]
    return re.sub(r"\n{3,}", "\n\n", "\n".join(lines)).strip()


def map_document(document, document_id: int, source: str) -> ExtractionResponse:
    from docling_core.types.doc import PictureItem, TableItem, TextItem

    pages = {number: ExtractedPage(pageNumber=number, text="", blocks=[]) for number in sorted(document.pages)}
    warnings = []
    for item, _ in document.iterate_items():
        description = None
        text = ""
        if isinstance(item, PictureItem):
            # OCR inside pictures is nested in the Docling tree. Keep it with its figure.
            text = "\n\n".join(
                child.text for child, _ in document.iterate_items(root=item, traverse_pictures=True)
                if isinstance(child, TextItem)
            )
            if item.meta is not None and item.meta.description is not None:
                description = clean_text(item.meta.description.text) or None
        elif isinstance(item, TableItem):
            text = item.export_to_markdown(doc=document)
        elif isinstance(item, TextItem):
            text = item.text
        else:
            continue
        text = clean_text(text)
        if not item.prov:
            warnings.append("BLOCK_WITHOUT_PAGE_PROVENANCE")
        for prov in item.prov:
            page = pages.get(prov.page_no)
            if page is None:
                warnings.append("BLOCK_WITHOUT_PAGE_PROVENANCE")
                continue
            box = prov.bbox
            page.blocks.append(ContentBlock(
                id=item.self_ref, type=item.label.value, text=text,
                bbox=BoundingBox(l=box.l, t=box.t, r=box.r, b=box.b, coordOrigin=box.coord_origin.value),
                description=description,
                descriptionMethod="vision_model" if description else None,
            ))
            if isinstance(item, PictureItem) and not description:
                warnings.append(f"PAGE_{prov.page_no}_PICTURE_DESCRIPTION_UNAVAILABLE")
    for page in pages.values():
        page.text = "\n\n".join(block.text for block in page.blocks if block.text)
        if not page.text:
            warnings.append(f"PAGE_{page.pageNumber}_NO_TEXT")
    return ExtractionResponse(
        documentId=document_id, source=source, pageCount=len(pages),
        pages=list(pages.values()), warnings=list(dict.fromkeys(warnings)),
    )


class DoclingExtractor:
    def __init__(self, settings: ExtractionSettings):
        self.settings = settings
        self._converter = None
        self._lock = Lock()

    def _load_converter(self):
        from docling.datamodel.base_models import InputFormat
        from docling.datamodel.accelerator_options import AcceleratorDevice, AcceleratorOptions
        from docling.datamodel.pipeline_options import (
            PdfPipelineOptions, PictureDescriptionVlmEngineOptions, EasyOcrOptions,
        )
        from docling.datamodel.vlm_engine_options import TransformersVlmEngineOptions
        from docling.document_converter import DocumentConverter, PdfFormatOption

        options = PdfPipelineOptions(
            do_ocr=True, do_table_structure=True,
            document_timeout=self.settings.timeout_seconds,
            do_picture_description=self.settings.picture_descriptions,
            generate_picture_images=self.settings.picture_descriptions,
            enable_remote_services=False,
            accelerator_options=AcceleratorOptions(device=AcceleratorDevice.CPU, num_threads=4),
        )
        options.ocr_options = EasyOcrOptions(
            lang=["vi", "en"],
            model_storage_directory=str(Path(self.settings.model_cache_dir) / "easyocr"),
        )
        if self.settings.picture_descriptions:
            description = PictureDescriptionVlmEngineOptions.from_preset(
                "smolvlm",
                engine_options=TransformersVlmEngineOptions(
                    device=AcceleratorDevice.CPU, torch_dtype="float32",
                    compile_model=False, quantized=False, load_in_8bit=False,
                ),
                prompt="Describe the diagram or image accurately, including labels, arrows and relationships. Do not invent missing details.",
                generation_config={"max_new_tokens": 64, "temperature": 0.0},
            )
            description.model_spec = description.model_spec.model_copy(
                update={"default_repo_id": self.settings.picture_model}, deep=True,
            )
            options.picture_description_options = description
        return DocumentConverter(
            allowed_formats=[InputFormat.PDF],
            format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=options)},
        )

    def extract(self, payload: bytes, document_id: int, source: str) -> ExtractionResponse:
        if not self._lock.acquire(blocking=False):
            raise HTTPException(503, "Extraction is busy; retry later.")
        receiver = sender = worker = None
        try:
            context = multiprocessing.get_context("spawn")
            receiver, sender = context.Pipe(duplex=False)
            worker = context.Process(
                target=_extract_in_worker,
                args=(self.settings, payload, document_id, source, sender),
                daemon=True,
            )
            # A process boundary makes model loading and VLM work cancellable too.
            worker.start()
            sender.close()
            if not receiver.poll(self.settings.timeout_seconds):
                raise HTTPException(504, "Document extraction timed out.")
            status, value = receiver.recv()
            if status != 200:
                raise HTTPException(status, value)
            return ExtractionResponse.model_validate(value)
        except (EOFError, OSError):
            raise HTTPException(503, "Extraction worker is unavailable.") from None
        finally:
            if worker is not None and worker.is_alive():
                worker.terminate()
            if worker is not None and worker.pid is not None:
                worker.join(timeout=2)
                if worker.is_alive():
                    worker.kill()
                    worker.join(timeout=2)
            if receiver is not None:
                receiver.close()
            if sender is not None:
                sender.close()
            self._lock.release()

    def _convert(self, payload: bytes, document_id: int, source: str) -> ExtractionResponse:
        try:
            from docling.datamodel.base_models import ConversionStatus, DocumentStream

            if self._converter is None:
                self._converter = self._load_converter()
            result = self._converter.convert(
                DocumentStream(name="document.pdf", stream=BytesIO(payload)),
                max_num_pages=self.settings.max_pages,
                max_file_size=self.settings.max_file_bytes,
                raises_on_error=False,
            )
            if result.has_timeout_errors():
                raise HTTPException(504, "Document extraction timed out.")
            if result.status != ConversionStatus.SUCCESS:
                raise HTTPException(422, "Document could not be fully extracted.")
            return map_document(result.document, document_id, source)
        except HTTPException:
            raise
        except Exception:
            # Avoid returning paths, document contents or model internals to callers.
            import logging
            logging.getLogger(__name__).exception("Docling extraction failed")
            raise HTTPException(503, "Document extraction is unavailable; check model and OCR setup.") from None


def _extract_in_worker(settings, payload, document_id, source, connection):
    try:
        result = DoclingExtractor(settings)._convert(payload, document_id, source)
        connection.send((200, result.model_dump()))
    except HTTPException as error:
        connection.send((error.status_code, error.detail))
    finally:
        connection.close()
