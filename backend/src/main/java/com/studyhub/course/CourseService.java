package com.studyhub.course;

import java.util.List;
import org.springframework.data.domain.Page;

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
import com.studyhub.course.dto.CreateCourseRequest;
import com.studyhub.course.dto.UpdateCourseRequest;
import com.studyhub.course.dto.CourseResponse;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import jakarta.persistence.criteria.Predicate;

@Service
public class CourseService {
    private final CourseRepository courseRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;

    public CourseService(CourseRepository courseRepository, WorkspaceRepository workspaceRepository, UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CourseResponse createCourse(Long workspaceId, CreateCourseRequest request, Long currentUserId) {
        Workspace workspace = workspaceRepository.findByIdAndOwnerId(workspaceId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        Course course = new Course(request.name(), request.description(), workspace, CourseStatus.PLANNED);
        return toResponse(courseRepository.save(course));
    }

    @Transactional (readOnly = true)
    public CourseResponse getCourse(Long courseId, Long currentUserId) {
        Course course = courseRepository.findByIdAndWorkspaceOwnerId(courseId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        return toResponse(course);
    }

    @Transactional (readOnly = true)
    public Page<CourseResponse> getAllCourses(Long userId, Long workspaceId, CourseStatus status, String q, Pageable pageable) {
        Specification<Course> filter = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("workspace").get("owner").get("id"), userId));

            if (workspaceId != null) {
                conditions.add(cb.equal(root.get("workspace").get("id"), workspaceId));
            }
            if (status != null) {
                conditions.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                conditions.add(cb.like(cb.lower(root.get("name")),"%" + q.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
        return courseRepository.findAll(filter, pageable).map(this::toResponse);
    }

    @Transactional
    public CourseResponse updateCourse(Long courseId, UpdateCourseRequest request, Long currentUserId) {
        Course course = courseRepository.findByIdAndWorkspaceOwnerId(courseId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        course.setName(request.name());
        course.setDescription(request.description());
        course.setStatus(request.status());
        courseRepository.flush();
        return toResponse(course);
    }

    @Transactional
    public void deleteCourse(Long courseId, Long currentUserId) {
        Course course = courseRepository.findByIdAndWorkspaceOwnerId(courseId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        courseRepository.delete(course);
    }

    private CourseResponse toResponse(Course course) {
        return new CourseResponse(course.getId(), course.getName(), course.getDescription(), course.getStatus());
    }
}
