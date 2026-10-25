"""Native Docling chunking, contextualized embedding text and item-level sources."""
import os
import re
import unicodedata
import warnings
from dataclasses import dataclass

from .schemas import BoundingBox, ChunkSourceReference, DocumentChunk


class ChunkingError(ValueError):
    """The document cannot be chunked without violating the output contract."""


@dataclass(frozen=True)
class ChunkingSettings:
    tokenizer_model: str = "intfloat/multilingual-e5-small"
    max_tokens: int = 384
    overlap_tokens: int = 48

    def __post_init__(self):
        if not self.tokenizer_model.strip():
            raise ValueError("Chunk tokenizer model must not be blank")
        if self.max_tokens <= 0 or not 0 <= self.overlap_tokens < self.max_tokens:
            raise ValueError("Require 0 <= overlap_tokens < max_tokens")

    @classmethod
    def from_env(cls):
        return cls(
            tokenizer_model=os.getenv("AI_CHUNK_TOKENIZER_MODEL", cls.tokenizer_model),
            max_tokens=int(os.getenv("AI_CHUNK_MAX_TOKENS", str(cls.max_tokens))),
            overlap_tokens=int(os.getenv("AI_CHUNK_OVERLAP_TOKENS", str(cls.overlap_tokens))),
        )


def _source_refs(doc_chunk, document):
    refs = []
    seen = set()
    for item in doc_chunk.meta.doc_items:
        if not item.prov:
            raise ChunkingError("Chunk item has no page provenance")
        for prov in item.prov:
            if prov.page_no not in document.pages:
                raise ChunkingError("Chunk item has invalid page provenance")
            box = prov.bbox
            key = (item.self_ref, prov.page_no, box.l, box.t, box.r, box.b, box.coord_origin.value)
            if key not in seen:
                seen.add(key)
                refs.append(ChunkSourceReference(
                    blockId=item.self_ref, pageNumber=prov.page_no,
                    bbox=BoundingBox(l=box.l, t=box.t, r=box.r, b=box.b,
                                     coordOrigin=box.coord_origin.value),
                ))
    if not refs:
        raise ChunkingError("Chunk has no page provenance")
    return refs


def _overlap_tail(text, budget, tokenizer):
    # Prefer whole words, never slice encoded tokens or break Unicode code points.
    starts = [m.start() for m in re.finditer(r"\S+", text)]
    tail = ""
    for start in reversed(starts):
        candidate = text[start:]
        if tokenizer.count_tokens(candidate) > budget:
            break
        tail = candidate
    return tail


def load_tokenizer(settings: ChunkingSettings):
    from docling_core.transforms.chunker.tokenizer.huggingface import HuggingFaceTokenizer
    from transformers import AutoTokenizer

    raw = AutoTokenizer.from_pretrained(settings.tokenizer_model, trust_remote_code=False)
    special_tokens = raw.num_special_tokens_to_add(pair=False)
    if settings.max_tokens > raw.model_max_length:
        raise ValueError("Chunk limit exceeds the tokenizer model context window")
    budget = settings.max_tokens - settings.overlap_tokens - special_tokens
    if budget <= 0:
        raise ValueError("Chunk budget must leave room for content and special tokens")
    return HuggingFaceTokenizer(tokenizer=raw, max_tokens=budget), special_tokens


def chunk_document(document, document_id: int, source: str, settings: ChunkingSettings,
                   tokenizer=None) -> list[DocumentChunk]:
    from docling.chunking import HybridChunker
    from docling_core.transforms.chunker.hierarchical_chunker import (
        ChunkingSerializerProvider, ChunkingDocSerializer, HierarchicalChunker,
    )
    from docling_core.transforms.serializer.markdown import MarkdownParams, MarkdownTableSerializer
    from docling_core.types.doc import DocItemLabel, TitleItem, SectionHeaderItem

    class SourceTextSerializerProvider(ChunkingSerializerProvider):
        def get_serializer(self, doc):
            return ChunkingDocSerializer(
                doc=doc, table_serializer=MarkdownTableSerializer(),
                params=MarkdownParams(
                    image_placeholder="", allowed_meta_names=set(), include_annotations=False,
                    include_picture_classification=False, enable_chart_tables=False,
                    traverse_pictures=True, escape_underscores=False, escape_html=False,
                ),
            )

    # Count special tokens too: downstream embedding must not silently truncate.
    tokenizer, special_tokens = load_tokenizer(settings) if tokenizer is None else (tokenizer, 0)

    body_budget = settings.max_tokens - settings.overlap_tokens - special_tokens
    if body_budget <= 0:
        raise ValueError("Chunk budget must leave room for content and special tokens")
    if tokenizer.get_max_tokens() != body_budget:
        raise ValueError("Tokenizer chunk limit must match the configured content budget")
    chunker = HybridChunker(tokenizer=tokenizer, merge_peers=True, repeat_table_header=True,
                            serializer_provider=SourceTextSerializerProvider(), always_emit_headings=True)
    # Docling may drop overlong context. Reject instead of silently losing headings.
    for section in HierarchicalChunker(serializer_provider=chunker.serializer_provider,
                                       always_emit_headings=True).chunk(dl_doc=document):
        context = chunker.contextualize(section.model_copy(update={"text": ""}))
        if tokenizer.count_tokens(context) >= body_budget:
            raise ChunkingError("Chunk heading/caption exceeds the available token budget")
    with warnings.catch_warnings(record=True) as captured:
        native_chunks = list(chunker.chunk(dl_doc=document))
    if any("Headers and captions" in str(w.message) for w in captured):
        raise ChunkingError("Chunk heading/caption leaves no room for source content")
    for warning in captured:
        warnings.warn(str(warning.message), warning.category, stacklevel=2)
    results = []
    previous_native = None
    for native in native_chunks:
        content = unicodedata.normalize("NFC", native.text).strip()
        heading_only = not content
        if heading_only:
            # Chunk metadata may contain generic DocItem snapshots without text.
            source_items = [item.get_ref().resolve(document) for item in native.meta.doc_items]
            content = "\n".join(unicodedata.normalize("NFC", item.text).strip()
                                for item in source_items
                                if isinstance(item, (TitleItem, SectionHeaderItem)) and item.text.strip())
        if not content:
            continue
        refs = _source_refs(native, document)
        headings = [unicodedata.normalize("NFC", heading) for heading in native.meta.headings or []]
        context_headings = headings[:-1] if heading_only and headings and headings[-1] == content else headings
        native = native.model_copy(update={"meta": native.meta.model_copy(update={"headings": context_headings or None})})
        overlap = ""
        overlap_refs = []
        # Tables use native header repetition, rather than duplicating partial rows.
        is_table = any(item.label == DocItemLabel.TABLE for item in native.meta.doc_items)
        previous_table = previous_native is not None and any(
            item.label == DocItemLabel.TABLE for item in previous_native.meta.doc_items)
        if results and settings.overlap_tokens and not is_table and not previous_table:
            previous = results[-1]
            if previous.headings == headings:
                overlap = _overlap_tail(previous.content, settings.overlap_tokens, tokenizer)
                if overlap:
                    overlap_refs = previous.sourceRefs

        def contextualize(tail):
            text = f"{tail}\n{content}" if tail else content
            return chunker.contextualize(native.model_copy(update={"text": text}))

        embedding_text = contextualize(overlap)
        # Tokenization is not additive across delimiters; check the actual final text.
        while overlap and tokenizer.count_tokens(embedding_text) + special_tokens > settings.max_tokens:
            overlap = overlap.split(maxsplit=1)[1] if len(overlap.split(maxsplit=1)) == 2 else ""
            embedding_text = contextualize(overlap)
        if not overlap:
            overlap_refs = []
        count = tokenizer.count_tokens(embedding_text) + special_tokens
        if count > settings.max_tokens:
            raise ChunkingError("Contextualized chunk exceeds the token limit")
        pages = [r.pageNumber for r in refs + overlap_refs]
        results.append(DocumentChunk(
            documentId=document_id, source=source, chunkIndex=len(results), content=content,
            embeddingText=embedding_text, headings=headings,
            pageStart=min(pages), pageEnd=max(pages),
            blockIds=list(dict.fromkeys(r.blockId for r in refs + overlap_refs)),
            sourceRefs=refs, overlapContent=overlap, overlapSourceRefs=overlap_refs,
            tokenCount=count, tokenizerModel=settings.tokenizer_model,
        ))
        previous_native = native
    return results
