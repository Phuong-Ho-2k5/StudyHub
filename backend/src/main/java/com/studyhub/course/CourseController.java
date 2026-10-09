package com.studyhub.course;

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

import com.studyhub.security.CustomUserDetails;
import com.studyhub.course.dto.CreateCourseRequest;
import com.studyhub.course.dto.UpdateCourseRequest;
import com.studyhub.course.dto.CourseResponse;
import com.studyhub.course.dto.CourseProgressResponse;

import org.springframework.data.domain.Pageable;

import com.studyhub.course.CourseService;
import java.util.List;
import org.springframework.data.domain.Page;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class CourseController {
    private final CourseService courseService;
    private final CourseProgressService courseProgressService;

    public CourseController(CourseService courseService, CourseProgressService courseProgressService) {
        this.courseService = courseService;
        this.courseProgressService = courseProgressService;
    }

    @PostMapping("/workspaces/{workspaceId}/courses")
    public ResponseEntity<CourseResponse> createCourse(
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        CourseResponse response = courseService.createCourse(workspaceId, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/courses/{id}")
    public CourseResponse getCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return courseService.getCourse(id, currentUser.getId());
    }

    @GetMapping("/courses/{courseId}/progress")
    public CourseProgressResponse getProgress(
            @PathVariable Long courseId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return courseProgressService.getProgress(courseId, currentUser.getId());
    }

    @PutMapping("/courses/{id}")
    public CourseResponse updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourseRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return courseService.updateCourse(id, request, currentUser.getId());
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Void> deleteCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        courseService.deleteCourse(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/courses")
    public Page<CourseResponse> getAllCourses(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return courseService.getAllCourses(currentUser.getId(), workspaceId, status, q, pageable);
    }
}
