package com.studyhub.admin.dto;

import com.studyhub.user.User;
import com.studyhub.user.UserRole;

public record AdminUserResponse(Long id, String email, String name, UserRole role) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
