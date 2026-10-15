package com.studyhub.admin;

import com.studyhub.admin.dto.AdminUserResponse;
import com.studyhub.admin.dto.UpdateUserRoleRequest;
import com.studyhub.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AdminUserResponse> list(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}/role")
    public AdminUserResponse updateRole(@PathVariable Long id, @Valid @RequestBody UpdateUserRoleRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return service.updateRole(id, request, user.getId());
    }
}
