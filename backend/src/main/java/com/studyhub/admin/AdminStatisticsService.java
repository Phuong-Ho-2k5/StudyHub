package com.studyhub.admin;

import com.studyhub.admin.dto.AdminStatisticsResponse;
import com.studyhub.course.CourseRepository;
import com.studyhub.user.UserRepository;
import com.studyhub.workspace.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatisticsService {
    private final UserRepository users;
    private final WorkspaceRepository workspaces;
    private final CourseRepository courses;

    public AdminStatisticsService(UserRepository users, WorkspaceRepository workspaces, CourseRepository courses) {
        this.users = users;
        this.workspaces = workspaces;
        this.courses = courses;
    }

    @Transactional(readOnly = true)
    public AdminStatisticsResponse getStatistics() {
        return new AdminStatisticsResponse(users.count(), workspaces.count(), courses.count());
    }
}
