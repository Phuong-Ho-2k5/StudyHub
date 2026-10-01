package com.studyhub.concept;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.studyhub.security.CustomUserDetails;
import com.studyhub.concept.dto.CreateConceptRequest;
import com.studyhub.concept.dto.UpdateConceptRequest;
import com.studyhub.concept.dto.AddPrerequisiteRequest;
import com.studyhub.concept.dto.ConceptResponse;

import com.studyhub.concept.ConceptService;
import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ConceptController {
    private final ConceptService conceptService;

    public ConceptController(ConceptService conceptService) {
        this.conceptService = conceptService;
    }

    @GetMapping("/concepts/{id}")
    public ResponseEntity<ConceptResponse> getConcept(
        @PathVariable Long id,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        ConceptResponse concept = conceptService.getConcept(id, currentUser.getId());
        return ResponseEntity.ok(concept);
    }

    @GetMapping("/concepts")
    public ResponseEntity<List<ConceptResponse>> getConceptFromCourse(
        @RequestParam Long courseId,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<ConceptResponse> concepts = conceptService.getConceptFromCourse(courseId, currentUser.getId());
        return ResponseEntity.ok(concepts);
    }

    @GetMapping("/courses/{courseId}/concepts")
    public ResponseEntity<List<ConceptResponse>> getConceptsWithLowConfidence(
        @PathVariable Long courseId,
        @RequestParam @Min(0) @Max(100) Integer confidenceBelow,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<ConceptResponse> concepts = conceptService.getConceptsWithLowConfidence(courseId, confidenceBelow, currentUser.getId());
        return ResponseEntity.ok(concepts);
    }

    @GetMapping("/concepts/{id}/prerequisites")
    public ResponseEntity<List<ConceptResponse>> getPrerequisites(
        @PathVariable Long id,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<ConceptResponse> prerequisites = conceptService.getPrerequisites(id, currentUser.getId());
        return ResponseEntity.ok(prerequisites);
    }

    @PostMapping("/courses/{courseId}/concepts")
    public ResponseEntity<ConceptResponse> createConcept(
        @PathVariable Long courseId,
        @Valid @RequestBody CreateConceptRequest request,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        ConceptResponse response = conceptService.createConcept(courseId, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/concepts/{id}/prerequisites")
    public ResponseEntity<Void> addPrerequisite(
        @PathVariable Long id,
        @Valid @RequestBody AddPrerequisiteRequest request,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        conceptService.addPrerequisite(id, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/concepts/{id}")
    public ResponseEntity<ConceptResponse> updateConcept(
        @PathVariable Long id,
        @Valid @RequestBody UpdateConceptRequest request,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        ConceptResponse response = conceptService.updateConcept(id, request, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/concepts/{id}")
    public ResponseEntity<Void> deleteConcept(
        @PathVariable Long id,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        conceptService.deleteConcept(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/concepts/{id}/prerequisites/{prerequisiteId}")
    public ResponseEntity<Void> removePrerequisite(
        @PathVariable Long id,
        @PathVariable Long prerequisiteId,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        conceptService.removePrerequisite(id, prerequisiteId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}