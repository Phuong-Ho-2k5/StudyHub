package com.studyhub.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.studyhub.concept.Concept;
import com.studyhub.concept.ConceptRepository;
import com.studyhub.course.Course;
import com.studyhub.course.CourseRepository;
import com.studyhub.document.Document;
import com.studyhub.document.DocumentRepository;
import com.studyhub.quiz.Quiz;
import com.studyhub.quiz.QuizRepository;
import com.studyhub.workspace.Workspace;
import com.studyhub.workspace.WorkspaceRepository;

@Service
public class ResourceAccessService {
    private final WorkspaceRepository workspaceRepository;
    private final CourseRepository courseRepository;
    private final DocumentRepository documentRepository;
    private final ConceptRepository conceptRepository;
    private final QuizRepository quizRepository;

    public ResourceAccessService(WorkspaceRepository workspaceRepository, CourseRepository courseRepository,
            DocumentRepository documentRepository, ConceptRepository conceptRepository, QuizRepository quizRepository) {
        this.workspaceRepository = workspaceRepository;
        this.courseRepository = courseRepository;
        this.documentRepository = documentRepository;
        this.conceptRepository = conceptRepository;
        this.quizRepository = quizRepository;
    }

    public Workspace requireWorkspace(Long workspaceId, Long userId) {
        return workspaceRepository.findByIdAndOwnerId(workspaceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
    }

    public Course requireCourse(Long courseId, Long userId) {
        return courseRepository.findByIdAndWorkspaceOwnerId(courseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }

    public Document requireDocument(Long documentId, Long userId) {
        return documentRepository.findByIdAndCourseWorkspaceOwnerId(documentId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    public Concept requireConcept(Long conceptId, Long userId) {
        return requireConcept(conceptId, userId, "Concept not found");
    }

    public Concept requirePrerequisiteConcept(Long conceptId, Long userId) {
        return requireConcept(conceptId, userId, "Prerequisite concept not found");
    }

    public Quiz requireQuiz(Long quizId, Long userId) {
        return quizRepository.findByIdAndCourseWorkspaceOwnerId(quizId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));
    }

    private Concept requireConcept(Long conceptId, Long userId, String notFoundReason) {
        return conceptRepository.findByIdAndCourseWorkspaceOwnerId(conceptId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundReason));
    }
}
