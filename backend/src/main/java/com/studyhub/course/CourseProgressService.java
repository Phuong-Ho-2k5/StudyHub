package com.studyhub.course;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.studyhub.concept.Concept;
import com.studyhub.concept.ConceptRepository;
import com.studyhub.course.dto.CourseProgressResponse;
import com.studyhub.security.ResourceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseProgressService {
    private final ConceptRepository conceptRepository;
    private final ResourceAccessService resourceAccessService;

    public CourseProgressService(ConceptRepository conceptRepository,
            ResourceAccessService resourceAccessService) {
        this.conceptRepository = conceptRepository;
        this.resourceAccessService = resourceAccessService;
    }

    @Transactional(readOnly = true)
    public CourseProgressResponse getProgress(Long courseId, Long currentUserId) {
        resourceAccessService.requireCourse(courseId, currentUserId);
        List<Concept> concepts = conceptRepository.findAllByCourseId(courseId);
        int total = concepts.size();
        if (total == 0) {
            return new CourseProgressResponse(courseId, 0, 0.0, 0, 0, 0, 0.0);
        }

        long totalConfidence = 0;
        int mastered = 0;
        int developing = 0;
        int needsReview = 0;
        for (Concept concept : concepts) {
            int confidence = concept.getConfidence();
            totalConfidence += confidence;
            if (confidence >= 80) {
                mastered++;
            } else if (confidence >= 50) {
                developing++;
            } else {
                needsReview++;
            }
        }

        return new CourseProgressResponse(courseId, total,
                round2((double) totalConfidence / total),
                mastered, developing, needsReview,
                round2(mastered * 100.0 / total));
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
