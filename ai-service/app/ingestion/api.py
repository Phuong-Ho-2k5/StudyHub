import hmac
import os
from functools import lru_cache

from fastapi import APIRouter, File, Form, HTTPException, UploadFile, Security
from fastapi.security import APIKeyHeader
from starlette.responses import JSONResponse

from .docling_parser import DoclingExtractor
from .schemas import ExtractionResponse
from .settings import ExtractionSettings

router = APIRouter(prefix="/internal/documents", tags=["Internal extraction"])
service_key = APIKeyHeader(name="X-AI-Service-Token", auto_error=False)


class ExtractionGuard:
    """Authenticate before multipart parsing and bound the entire request stream."""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or not scope["path"].startswith("/internal/"):
            return await self.app(scope, receive, send)
        token = os.getenv("AI_SERVICE_TOKEN", "")
        headers = dict(scope["headers"])
        supplied = headers.get(b"x-ai-service-token", b"")
        if not token:
            return await JSONResponse({"detail": "AI service token is not configured."}, 503)(scope, receive, send)
        if not hmac.compare_digest(supplied, token.encode("utf-8")):
            return await JSONResponse({"detail": "Invalid internal service token."}, 401)(scope, receive, send)
        maximum = ExtractionSettings.from_env().max_file_bytes + 1024 * 1024
        try:
            length = int(headers.get(b"content-length", b"0"))
        except ValueError:
            return await JSONResponse({"detail": "Invalid Content-Length."}, 400)(scope, receive, send)
        if length > maximum:
            return await JSONResponse({"detail": "Request is too large."}, 413)(scope, receive, send)
        received = 0

        async def bounded_receive():
            nonlocal received
            message = await receive()
            received += len(message.get("body", b""))
            if received > maximum:
                raise HTTPException(413, "Request is too large.")
            return message

        await self.app(scope, bounded_receive, send)


@lru_cache(maxsize=1)
def get_extractor(settings: ExtractionSettings) -> DoclingExtractor:
    return DoclingExtractor(settings)


@router.post("/extract", response_model=ExtractionResponse,
             dependencies=[Security(service_key)], responses={
    401: {"description": "Invalid internal service token"},
    413: {"description": "File or page limit exceeded"},
    415: {"description": "Invalid or protected PDF"},
    422: {"description": "Invalid fields or incomplete conversion"},
    503: {"description": "Model, OCR or extraction worker unavailable"},
    504: {"description": "Conversion timed out"},
})
def extract_document(documentId: int = Form(gt=0), file: UploadFile = File(),
                     includeChunks: bool = Form(False)) -> ExtractionResponse:
    settings = ExtractionSettings.from_env()
    payload = file.file.read(settings.max_file_bytes + 1)
    if len(payload) > settings.max_file_bytes:
        raise HTTPException(413, "PDF exceeds the file size limit.")
    if file.content_type != "application/pdf" or not payload.startswith(b"%PDF-"):
        raise HTTPException(415, "A valid PDF is required.")
    import pypdfium2 as pdfium
    from docling.utils.locks import pypdfium2_lock

    try:
        with pypdfium2_lock, pdfium.PdfDocument(payload) as pdf:
            count = len(pdf)
            if count < 1:
                raise HTTPException(415, "PDF must contain a page.")
            if count > settings.max_pages:
                raise HTTPException(413, "PDF exceeds the page limit.")
    except pdfium.PdfiumError:
        raise HTTPException(415, "Invalid or password-protected PDF.") from None
    source = (file.filename or "document.pdf").replace("\\", "/").rsplit("/", 1)[-1]
    extractor = get_extractor(settings)
    return extractor.extract(payload, documentId, source, include_chunks=True) if includeChunks else extractor.extract(payload, documentId, source)
