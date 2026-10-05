package com.studyhub.question;

import java.util.List;

import com.studyhub.question.dto.QuestionRequest;
import com.studyhub.question.dto.QuestionResponse;
import com.studyhub.security.CustomUserDetails;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api")
public class QuestionController {
    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/quizzes/{quizId}/questions")
    public ResponseEntity<QuestionResponse> create(@PathVariable Long quizId,
            @Valid @RequestBody QuestionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(questionService.create(quizId, request, currentUser.getId()));
    }

    @GetMapping("/quizzes/{quizId}/questions")
    public ResponseEntity<List<QuestionResponse>> list(@PathVariable Long quizId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(questionService.list(quizId, currentUser.getId()));
    }

    @GetMapping("/questions/{id}")
    public ResponseEntity<QuestionResponse> get(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(questionService.get(id, currentUser.getId()));
    }

    @PutMapping("/questions/{id}")
    public ResponseEntity<QuestionResponse> update(@PathVariable Long id,
            @Valid @RequestBody QuestionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(questionService.update(id, request, currentUser.getId()));
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        questionService.delete(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
