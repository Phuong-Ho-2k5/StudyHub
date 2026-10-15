package com.studyhub.admin;

import com.studyhub.admin.dto.AdminUserResponse;
import com.studyhub.admin.dto.UpdateUserRoleRequest;
import com.studyhub.user.User;
import com.studyhub.user.UserRepository;
import com.studyhub.user.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {
    private final UserRepository repository;

    public AdminUserService(UserRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(Pageable pageable) {
        return repository.findAll(pageable).map(AdminUserResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(Long id) {
        return AdminUserResponse.from(requireUser(id));
    }

    @Transactional
    public AdminUserResponse updateRole(Long id, UpdateUserRoleRequest request, Long currentUserId) {
        User user = requireUser(id);
        UserRole role = UserRole.valueOf(request.role());
        if (id.equals(currentUserId) && role != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot demote your own admin account");
        }
        user.changeRole(role);
        return AdminUserResponse.from(user);
    }

    private User requireUser(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
