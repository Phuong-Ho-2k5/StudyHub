package com.studyhub.ai.feedback;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AIFeedbackRepository extends JpaRepository<AIFeedback,Long> {
    Optional<AIFeedback> findByUserIdAndMessageId(Long userId,Long messageId);
    java.util.List<AIFeedback> findAllByUserIdAndMessageIdIn(Long userId,java.util.Collection<Long> messageIds);
}
