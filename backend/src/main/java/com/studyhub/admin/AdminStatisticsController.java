package com.studyhub.admin;

import com.studyhub.admin.dto.AdminStatisticsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/statistics")
public class AdminStatisticsController {
    private final AdminStatisticsService service;

    public AdminStatisticsController(AdminStatisticsService service) {
        this.service = service;
    }

    @GetMapping
    public AdminStatisticsResponse getStatistics() {
        return service.getStatistics();
    }
}
