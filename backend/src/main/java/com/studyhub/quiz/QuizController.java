package com.studyhub.quiz;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studyhub.quiz.dto.CreateQuizRequest;
import com.studyhub.quiz.dto.QuizResponse;
import com.studyhub.quiz.dto.UpdateQuizRequest;
import com.studyhub.security.CustomUserDetails;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class QuizController {
    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/courses/{courseId}/quizzes")
    public ResponseEntity<QuizResponse> create(@PathVariable Long courseId,
            @Valid @RequestBody CreateQuizRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(quizService.create(courseId, request, currentUser.getId()));
    }

    @GetMapping("/courses/{courseId}/quizzes")
    public ResponseEntity<List<QuizResponse>> list(@PathVariable Long courseId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(quizService.list(courseId, currentUser.getId()));
    }

    @GetMapping("/quizzes/{id}")
    public ResponseEntity<QuizResponse> get(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(quizService.get(id, currentUser.getId()));
    }

    @PutMapping("/quizzes/{id}")
    public ResponseEntity<QuizResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateQuizRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(quizService.update(id, request, currentUser.getId()));
    }

    @DeleteMapping("/quizzes/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        quizService.delete(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
