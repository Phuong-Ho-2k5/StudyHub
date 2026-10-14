package com.studyhub.notification.dto;

import java.time.Instant;
import com.studyhub.notification.Notification;
import com.studyhub.notification.NotificationType;

public record NotificationResponse(Long id, NotificationType type, String content, boolean read, Instant createdAt) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getContent(),
                notification.isRead(), notification.getCreatedAt().toInstant());
    }
}
