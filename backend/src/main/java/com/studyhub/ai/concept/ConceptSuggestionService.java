package com.studyhub.ai.concept;

import static com.studyhub.ai.concept.ConceptSuggestionModels.*;
import com.studyhub.ai.concept.dto.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConceptSuggestionService {
    private final ConceptSuggestionRepository repository;
    private final ConceptExtractionClient ai;
    private final ConceptEvidenceValidator validator;
    public ConceptSuggestionService(ConceptSuggestionRepository repository,ConceptExtractionClient ai,ConceptEvidenceValidator validator) {
        this.repository=repository;this.ai=ai;this.validator=validator;
    }
    public DraftBatch generate(Long courseId,Long documentId,Long userId,GenerateConceptSuggestionsRequest request) {
        var window=repository.readWindow(courseId,documentId,userId,request.startChunkIndex(),request.chunkLimit());
        var extraction=ai.extract(window,request.maxSuggestions());
        return repository.saveDraft(courseId,documentId,userId,window,validator.validate(window,extraction,request.maxSuggestions()));
    }
    public DraftBatch getBatch(Long courseId,Long documentId,Long userId,String batchId) {
        return repository.findOwnedBatch(courseId,documentId,userId,batchId);
    }
    public ConfirmationResult confirm(Long courseId,Long documentId,Long userId,String batchId,Long itemId,ConfirmConceptSuggestionRequest request) {
        try { return repository.confirm(courseId,documentId,userId,batchId,itemId,request.name(),request.description()); }
        catch(DataIntegrityViolationException conflict) {
            // Outside the TransactionTemplate: no work continues in a failed PostgreSQL transaction.
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Concept name conflicts or suggestion could not be confirmed.");
        }
    }
}
