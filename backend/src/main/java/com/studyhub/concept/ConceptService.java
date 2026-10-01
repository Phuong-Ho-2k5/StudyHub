package com.studyhub.concept;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.studyhub.user.User;
import com.studyhub.user.UserRepository;
import com.studyhub.workspace.Workspace;
import com.studyhub.workspace.WorkspaceRepository;
import com.studyhub.course.Course;
import com.studyhub.course.CourseRepository;
import com.studyhub.concept.Concept;
import com.studyhub.concept.ConceptRepository;
import com.studyhub.concept.ConceptDependency;
import com.studyhub.concept.ConceptDependencyRepository;
import com.studyhub.concept.dto.CreateConceptRequest;
import com.studyhub.concept.dto.UpdateConceptRequest;
import com.studyhub.concept.dto.AddPrerequisiteRequest;
import com.studyhub.concept.dto.ConceptResponse;

@Service
@Transactional
public class ConceptService {
    private final ConceptRepository conceptRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final CourseRepository courseRepository;
    private final ConceptDependencyRepository conceptDependencyRepository;

    public ConceptService(ConceptRepository conceptRepository, UserRepository userRepository, WorkspaceRepository workspaceRepository, CourseRepository courseRepository, ConceptDependencyRepository conceptDependencyRepository) {
        this.conceptRepository = conceptRepository;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.courseRepository = courseRepository;
        this.conceptDependencyRepository = conceptDependencyRepository;
    }

    public List<ConceptResponse> getConceptFromCourse(Long courseId, Long currentUserId) {
        Course course = courseRepository.findByIdAndWorkspaceOwnerId(courseId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        List<Concept> concept = conceptRepository.findAllByCourseId(courseId);
        return concept.stream().map(this::toResponse).toList();
    }

    public ConceptResponse getConcept(Long id, Long currentUserId) {
        Concept concept = conceptRepository.findByIdAndCourseWorkspaceOwnerId(id, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept not found"));
        return toResponse(concept);
    }

    public List<ConceptResponse> getConceptsWithLowConfidence(Long courseId, Integer confidenceThreshold, Long currentUserId) {
        Course course = courseRepository.findByIdAndWorkspaceOwnerId(courseId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        List<Concept> concepts = conceptRepository.findAllByCourseIdAndConfidenceLessThan(courseId, confidenceThreshold);
        return concepts.stream().map(this::toResponse).toList();
    }

    public ConceptResponse createConcept(Long courseId, CreateConceptRequest request, Long currentUserId) {
        Course course = courseRepository.findByIdAndWorkspaceOwnerId(courseId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        Concept concept = conceptRepository.save(new Concept(request.name(), request.description(), request.confidence(), request.status(), course));
        return toResponse(concept);
    }

    public ConceptResponse updateConcept(Long id, UpdateConceptRequest request, Long currentUserId) {
        Concept concept = conceptRepository.findByIdAndCourseWorkspaceOwnerId(id, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept not found"));
        concept.setName(request.name());
        concept.setDescription(request.description());
        concept.setConfidence(request.confidence());
        concept.setStatus(request.status());
        return toResponse(conceptRepository.save(concept));
    }

    public void deleteConcept(Long id, Long currentUserId) {
        Concept concept = conceptRepository.findByIdAndCourseWorkspaceOwnerId(id, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept not found"));
        conceptRepository.delete(concept);
    }

    public void addPrerequisite(Long conceptId, AddPrerequisiteRequest request, Long currentUserId) {
        Long prerequisiteId = request.prerequisiteId();
        Concept concept = conceptRepository.findByIdAndCourseWorkspaceOwnerId(conceptId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept not found"));
        Concept prerequisite = conceptRepository.findByIdAndCourseWorkspaceOwnerId(prerequisiteId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prerequisite concept not found"));
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
        Concept concept = conceptRepository.findByIdAndCourseWorkspaceOwnerId(conceptId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept not found"));
        return conceptDependencyRepository.findAllByDependentConcept_Id(conceptId).stream()
                .map(ConceptDependency::getPrerequisiteConcept)
                .map(this::toResponse)
                .toList();
    }

    public void removePrerequisite(Long conceptId, Long prerequisiteId, Long currentUserId) {
        conceptRepository.findByIdAndCourseWorkspaceOwnerId(conceptId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept not found"));
        conceptRepository.findByIdAndCourseWorkspaceOwnerId(prerequisiteId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prerequisite concept not found"));
        ConceptDependency prerequisite = conceptDependencyRepository.findByDependentConcept_IdAndPrerequisiteConcept_Id(conceptId, prerequisiteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prerequisite not found"));
        conceptDependencyRepository.delete(prerequisite);
    }

    private ConceptResponse toResponse(Concept concept) {
        return new ConceptResponse(concept.getId(), concept.getCourse().getId(), concept.getName(), concept.getDescription(), concept.getConfidence(), concept.getStatus());
    }
}
