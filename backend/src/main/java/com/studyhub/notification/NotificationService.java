package com.studyhub.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.studyhub.notification.dto.NotificationResponse;
import com.studyhub.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createQuizSubmittedNotification(Long userId, Long quizId, double questionPercentage) {
        String score = BigDecimal.valueOf(questionPercentage).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        repository.save(new Notification(userRepository.getReferenceById(userId), NotificationType.QUIZ_SUBMITTED,
                "Bạn đã hoàn thành quiz #" + quizId + ", đạt " + score + "%."));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable).map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long id, Long userId) {
        Notification notification = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.markAsRead();
        return NotificationResponse.from(notification);
    }
}
