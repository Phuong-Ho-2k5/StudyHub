package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.studyhub.document.dto.DocumentExtractionResponse;
import com.studyhub.document.dto.DocumentExtractionResponse.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DocumentIngestionServiceTest {
    DocumentService documents = mock(DocumentService.class);
    DocumentExtractionClient extraction = mock(DocumentExtractionClient.class);
    DocumentEmbeddingClient embeddings = mock(DocumentEmbeddingClient.class);
    DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
    DocumentIngestionRepository queue = mock(DocumentIngestionRepository.class);
    DocumentIngestionRepository.Claim claim = new DocumentIngestionRepository.Claim("run",7L,9L,"token");
    DocumentIngestionService service = new DocumentIngestionService(documents, extraction, embeddings, chunks, queue);
    byte[] pdf = {1};
    DocumentService.ExtractionSource source = new DocumentService.ExtractionSource(7L, "lesson.pdf", "key.pdf", pdf);

    List<Chunk> prepare(int count) {
        var items = new ArrayList<Chunk>();
        for (int i = 0; i < count; i++) items.add(new Chunk(7L, "lesson.pdf", i, "content" + i,
                "Heading\ncontent" + i, List.of("Heading"), 1, 1, List.of("block"),
                List.of(new SourceReference("block", 1, new BoundingBox(1,2,3,4,"TOPLEFT"))), "", List.of(), 4, "tokenizer"));
        when(documents.readForExtraction(7L, 9L)).thenReturn(source);
        when(queue.claimNow(7L,9L)).thenReturn(claim);
        when(extraction.extract(7L, "lesson.pdf", pdf, true)).thenReturn(new DocumentExtractionResponse(
                7L, "lesson.pdf", 1, List.of(new ExtractedPage(1,"text",List.of())), List.of("warning"), items));
        when(embeddings.embed(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return new DocumentEmbeddingClient.EmbeddingBatch("model@rev", 3,
                    texts.stream().map(t -> List.of(1.0, 0.0, 0.0)).toList());
        });
        return items;
    }

    @Test void batchesAllTextsBeforeSinglePersistenceAndReturnsSummary() {
        var items = prepare(33);
        var result = service.ingest(7L, 9L);
        assertEquals(33, result.chunkCount());
        assertEquals("model@rev", result.embeddingModel());
        assertEquals(3, result.embeddingDimensions());
        assertEquals(List.of("warning"), result.warnings());
        var order = inOrder(embeddings, chunks);
        order.verify(embeddings).embed(items.subList(0,32).stream().map(Chunk::embeddingText).toList());
        order.verify(embeddings).embed(items.subList(32,33).stream().map(Chunk::embeddingText).toList());
        order.verify(chunks).persist(eq(claim), eq(source), eq(items), anyList(), eq("model@rev"), eq(3));
        verify(queue, times(3)).renew(claim);
        assertEquals("run",result.ingestionId());
    }

    @Test void emptyExtractionIs422WithNoModelOrPersistence() {
        prepare(0);
        assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.ingest(7L,9L)).getStatusCode().value());
        verifyNoInteractions(embeddings, chunks);
        verify(queue).fail(claim,"NO_CONTENT");
    }

    @Test void failedLaterBatchNeverPersistsAnyChunks() {
        prepare(33);
        when(embeddings.embed(List.of("Heading\ncontent32"))).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));
        assertThrows(ResponseStatusException.class, () -> service.ingest(7L,9L));
        verifyNoInteractions(chunks);
        verify(queue).fail(claim,"AI_INVALID_RESPONSE");
    }

    @Test void mixedModelsAcrossBatchesNeverPersist() {
        prepare(33);
        when(embeddings.embed(List.of("Heading\ncontent32"))).thenReturn(
                new DocumentEmbeddingClient.EmbeddingBatch("other@rev",3,List.of(List.of(1.0,0.0,0.0))));
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> service.ingest(7L,9L)).getStatusCode().value());
        verifyNoInteractions(chunks);
    }

    @Test void ownerFailureStopsBeforeAnyUpstreamCalls() {
        when(documents.readForExtraction(7L,9L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.ingest(7L,9L)).getStatusCode().value());
        verifyNoInteractions(extraction, embeddings, chunks);
        verifyNoInteractions(queue);
    }

    @Test void lostClaimStopsBeforeCallingAi() {
        prepare(1);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT)).when(queue).renew(claim);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.ingest(7L,9L)).getStatusCode().value());
        verifyNoInteractions(extraction,embeddings,chunks);
    }

    @Test void workerMarksFailedWhenSourceCannotBeRead() {
        when(documents.readForExtraction(7L,9L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        assertThrows(ResponseStatusException.class,()->service.process(claim));
        verify(queue).fail(claim,"SOURCE_CHANGED");
        verifyNoInteractions(extraction,embeddings,chunks);
    }
}
