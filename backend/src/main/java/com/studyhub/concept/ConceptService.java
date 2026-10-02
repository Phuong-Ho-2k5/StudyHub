package com.studyhub.concept;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.studyhub.course.Course;
import com.studyhub.concept.dto.CreateConceptRequest;
import com.studyhub.concept.dto.UpdateConceptRequest;
import com.studyhub.concept.dto.AddPrerequisiteRequest;
import com.studyhub.concept.dto.ConceptResponse;
import com.studyhub.security.ResourceAccessService;

@Service
@Transactional
public class ConceptService {
    private final ConceptRepository conceptRepository;
    private final ConceptDependencyRepository conceptDependencyRepository;
    private final ResourceAccessService resourceAccessService;

    public ConceptService(ConceptRepository conceptRepository,
            ConceptDependencyRepository conceptDependencyRepository, ResourceAccessService resourceAccessService) {
        this.conceptRepository = conceptRepository;
        this.conceptDependencyRepository = conceptDependencyRepository;
        this.resourceAccessService = resourceAccessService;
    }

    public List<ConceptResponse> getConceptFromCourse(Long courseId, Long currentUserId) {
        resourceAccessService.requireCourse(courseId, currentUserId);
        List<Concept> concept = conceptRepository.findAllByCourseId(courseId);
        return concept.stream().map(this::toResponse).toList();
    }

    public ConceptResponse getConcept(Long id, Long currentUserId) {
        Concept concept = resourceAccessService.requireConcept(id, currentUserId);
        return toResponse(concept);
    }

    public List<ConceptResponse> getConceptsWithLowConfidence(Long courseId, Integer confidenceThreshold, Long currentUserId) {
        resourceAccessService.requireCourse(courseId, currentUserId);
        List<Concept> concepts = conceptRepository.findAllByCourseIdAndConfidenceLessThan(courseId, confidenceThreshold);
        return concepts.stream().map(this::toResponse).toList();
    }

    public ConceptResponse createConcept(Long courseId, CreateConceptRequest request, Long currentUserId) {
        Course course = resourceAccessService.requireCourse(courseId, currentUserId);
        Concept concept = conceptRepository.save(new Concept(request.name(), request.description(), request.confidence(), request.status(), course));
        return toResponse(concept);
    }

    public ConceptResponse updateConcept(Long id, UpdateConceptRequest request, Long currentUserId) {
        Concept concept = resourceAccessService.requireConcept(id, currentUserId);
        concept.setName(request.name());
        concept.setDescription(request.description());
        concept.setConfidence(request.confidence());
        concept.setStatus(request.status());
        return toResponse(conceptRepository.save(concept));
    }

    public void deleteConcept(Long id, Long currentUserId) {
        Concept concept = resourceAccessService.requireConcept(id, currentUserId);
        conceptRepository.delete(concept);
    }

    public void addPrerequisite(Long conceptId, AddPrerequisiteRequest request, Long currentUserId) {
        Long prerequisiteId = request.prerequisiteId();
        Concept concept = resourceAccessService.requireConcept(conceptId, currentUserId);
        Concept prerequisite = resourceAccessService.requirePrerequisiteConcept(prerequisiteId, currentUserId);
        if (concept.getId().equals(prerequisite.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A concept cannot be a prerequisite of itself");
        }
        if (!concept.getCourse().getId().equals(prerequisite.getCourse().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prerequisite concept must belong to the same course");
        }
        if (conceptDependencyRepository.existsByDependentConcept_IdAndPrerequisiteConcept_Id(conceptId, prerequisiteId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prerequisite already exists for this concept");
        }
        ConceptDependency dependency = new ConceptDependency(prerequisite, concept);
        conceptDependencyRepository.save(dependency);
    }

    public List<ConceptResponse> getPrerequisites(Long conceptId, Long currentUserId) {
        resourceAccessService.requireConcept(conceptId, currentUserId);
        return conceptDependencyRepository.findAllByDependentConcept_Id(conceptId).stream()
                .map(ConceptDependency::getPrerequisiteConcept)
                .map(this::toResponse)
                .toList();
    }

    public void removePrerequisite(Long conceptId, Long prerequisiteId, Long currentUserId) {
        resourceAccessService.requireConcept(conceptId, currentUserId);
        resourceAccessService.requirePrerequisiteConcept(prerequisiteId, currentUserId);
        ConceptDependency prerequisite = conceptDependencyRepository.findByDependentConcept_IdAndPrerequisiteConcept_Id(conceptId, prerequisiteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prerequisite not found"));
        conceptDependencyRepository.delete(prerequisite);
    }

    private ConceptResponse toResponse(Concept concept) {
        return new ConceptResponse(concept.getId(), concept.getCourse().getId(), concept.getName(), concept.getDescription(), concept.getConfidence(), concept.getStatus());
    }
}
