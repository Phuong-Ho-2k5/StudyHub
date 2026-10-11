package com.studyhub.quiz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyhub.quiz.dto.QuizHistoryResponse;

@Service
public class QuizHistoryService {
    private final QuizAttemptRepository attemptRepository;

    public QuizHistoryService(QuizAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public Page<QuizHistoryResponse> getHistory(Long userId, Pageable pageable) {
        return attemptRepository.findByUserId(userId, pageable)
                .map(QuizHistoryResponse::from);
    }
}
