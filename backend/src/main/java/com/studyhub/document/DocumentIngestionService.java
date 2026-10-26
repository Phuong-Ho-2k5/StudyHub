package com.studyhub.document;
import com.studyhub.document.dto.DocumentIngestionResponse;
import com.studyhub.document.dto.DocumentExtractionResponse.Chunk;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentIngestionService {
    private final DocumentService documents;
    private final DocumentExtractionClient extraction;
    private final DocumentEmbeddingClient embeddings;
    private final DocumentChunkRepository chunks;

    public DocumentIngestionService(DocumentService documents, DocumentExtractionClient extraction,
            DocumentEmbeddingClient embeddings, DocumentChunkRepository chunks) {
        this.documents = documents;
        this.extraction = extraction;
        this.embeddings = embeddings;
        this.chunks = chunks;
    }

    // The source read commits before extraction/model calls; only persist() opens a write transaction.
    public DocumentIngestionResponse ingest(Long documentId, Long userId) {
        var source = documents.readForExtraction(documentId, userId);
        var extracted = extraction.extract(source.id(), source.fileName(), source.bytes(), true);
        var items = extracted.chunks();
        if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Document contains no ingestible chunks.");
        for (var chunk : items) embeddings.requireTokenizer(chunk.tokenizerModel());
        var vectors = new ArrayList<List<Double>>();
        String model = null;
        int dimensions = 0;
        for (int start = 0; start < items.size(); start += 32) {
            var texts = items.subList(start, Math.min(start + 32, items.size())).stream().map(Chunk::embeddingText).toList();
            var batch = embeddings.embed(texts);
            if (model != null && (!model.equals(batch.model()) || dimensions != batch.dimensions()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service changed embedding configuration between batches.");
            model = batch.model();
            dimensions = batch.dimensions();
            vectors.addAll(batch.vectors());
        }
        chunks.persist(source, userId, items, vectors, model, dimensions);
        return new DocumentIngestionResponse(documentId, items.size(), model, dimensions, extracted.warnings());
    }
}
