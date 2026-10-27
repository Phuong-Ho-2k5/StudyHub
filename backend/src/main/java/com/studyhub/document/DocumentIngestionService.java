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
    private final DocumentIngestionRepository queue;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(DocumentIngestionService.class);

    public DocumentIngestionService(DocumentService documents, DocumentExtractionClient extraction,
            DocumentEmbeddingClient embeddings, DocumentChunkRepository chunks, DocumentIngestionRepository queue) {
        this.documents = documents;
        this.extraction = extraction;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.queue = queue;
    }

    // The source read commits before extraction/model calls; only persist() opens a write transaction.
    public DocumentIngestionResponse ingest(Long documentId, Long userId) {
        var source = documents.readForExtraction(documentId, userId);
        var claim=queue.claimNow(documentId,userId);
        return execute(claim,source);
    }

    public DocumentIngestionResponse process(DocumentIngestionRepository.Claim claim) {
        DocumentService.ExtractionSource source;
        try {source=documents.readForExtraction(claim.documentId(),claim.userId());}
        catch(RuntimeException error){recordFailure(claim,error);throw error;}
        return execute(claim,source);
    }

    private DocumentIngestionResponse execute(DocumentIngestionRepository.Claim claim,DocumentService.ExtractionSource source) {
        try {
        queue.renew(claim);
        var extracted = extraction.extract(source.id(), source.fileName(), source.bytes(), true);
        var items = extracted.chunks();
        if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Document contains no ingestible chunks.");
        for (var chunk : items) embeddings.requireTokenizer(chunk.tokenizerModel());
        var vectors = new ArrayList<List<Double>>();
        String model = null;
        int dimensions = 0;
        for (int start = 0; start < items.size(); start += 32) {
            queue.renew(claim);
            var texts = items.subList(start, Math.min(start + 32, items.size())).stream().map(Chunk::embeddingText).toList();
            var batch = embeddings.embed(texts);
            if (model != null && (!model.equals(batch.model()) || dimensions != batch.dimensions()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service changed embedding configuration between batches.");
            model = batch.model();
            dimensions = batch.dimensions();
            vectors.addAll(batch.vectors());
        }
        chunks.persist(claim, source, items, vectors, model, dimensions);
        return new DocumentIngestionResponse(source.id(), items.size(), model, dimensions, extracted.warnings(),claim.id());
        } catch(RuntimeException error){recordFailure(claim,error);throw error;}
    }

    private void recordFailure(DocumentIngestionRepository.Claim claim,RuntimeException error) {
        String code="INGESTION_FAILED";
        if(error instanceof ResponseStatusException status)code=switch(status.getStatusCode().value()){
            case 404->"DOCUMENT_REMOVED";case 409->"SOURCE_CHANGED";case 422->"NO_CONTENT";
            case 502->"AI_INVALID_RESPONSE";case 503->"AI_UNAVAILABLE";case 504->"AI_TIMEOUT";
            case 413->"PDF_TOO_LARGE";case 415->"INVALID_PDF";default->"INGESTION_FAILED";
        };
        try {queue.fail(claim,code);}
        catch(RuntimeException ignored){log.warn("Could not record ingestion failure; claim will expire for recovery");}
    }
}
