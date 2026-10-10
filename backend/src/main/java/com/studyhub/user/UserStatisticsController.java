package com.studyhub.user;

import com.studyhub.security.CustomUserDetails;
import com.studyhub.user.dto.UserStatisticsResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserStatisticsController {
    private final UserStatisticsService service;

    public UserStatisticsController(UserStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/statistics")
    public UserStatisticsResponse getStatistics(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return service.getStatistics(currentUser.getId());
    }
}
