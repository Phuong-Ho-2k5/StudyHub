package com.studyhub.notification;

import com.studyhub.notification.dto.NotificationResponse;
import com.studyhub.notification.dto.UnreadCountResponse;
import com.studyhub.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    public Page<NotificationResponse> list(@AuthenticationPrincipal CustomUserDetails user,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(user.getId(), pageable);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse countUnread(@AuthenticationPrincipal CustomUserDetails user) {
        return new UnreadCountResponse(service.countUnread(user.getId()));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.markAsRead(id, user.getId());
    }
}
