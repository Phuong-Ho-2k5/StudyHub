package com.studyhub.workspace;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studyhub.security.CustomUserDetails;
import com.studyhub.workspace.dto.CreateWorkspaceRequest;
import com.studyhub.workspace.dto.UpdateWorkspaceRequest;
import com.studyhub.workspace.dto.WorkspaceResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @Valid @RequestBody CreateWorkspaceRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        WorkspaceResponse response = workspaceService.createWorkspace(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<WorkspaceResponse> getAllWorkspaces(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return workspaceService.getAllWorkspaces(currentUser.getId());
    }

    @GetMapping("/{id}")
    public WorkspaceResponse getWorkspaceById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return workspaceService.getWorkspace(id, currentUser.getId());
    }

    @PutMapping("/{id}")
    public WorkspaceResponse updateWorkspace(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWorkspaceRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return workspaceService.updateWorkspace(id, request, currentUser.getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkspace(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        workspaceService.deleteWorkspace(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
