package com.studyhub.quiz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studyhub.quiz.dto.QuizHistoryResponse;
import com.studyhub.security.CustomUserDetails;

@RestController
@RequestMapping("/api/users/me")
public class QuizHistoryController {
    private final QuizHistoryService historyService;

    public QuizHistoryController(QuizHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/quiz-history")
    public Page<QuizHistoryResponse> getHistory(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PageableDefault(size = 10, sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return historyService.getHistory(currentUser.getId(), pageable);
    }
}
