package com.studyhub.workspace;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.studyhub.user.User;
import com.studyhub.user.UserRepository;
import com.studyhub.workspace.dto.CreateWorkspaceRequest;
import com.studyhub.workspace.dto.UpdateWorkspaceRequest;
import com.studyhub.workspace.dto.WorkspaceResponse;

@Service
public class WorkspaceService {
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository, UserRepository userRepository) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, Long currentUserId) {
        User owner = userRepository.getReferenceById(currentUserId);
        Workspace workspace = new Workspace(request.name(), request.description(), owner);
        return toResponse(workspaceRepository.save(workspace));
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(Long workspaceId, Long currentUserId) {
        return toResponse(findOwnedWorkspace(workspaceId, currentUserId));
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getAllWorkspaces(Long currentUserId) {
        return workspaceRepository.findAllByOwnerId(currentUserId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(Long workspaceId, UpdateWorkspaceRequest request, Long currentUserId) {
        Workspace workspace = findOwnedWorkspace(workspaceId, currentUserId);
        workspace.setName(request.name());
        workspace.setDescription(request.description());
        workspaceRepository.flush();
        return toResponse(workspace);
    }

    @Transactional
    public void deleteWorkspace(Long workspaceId, Long currentUserId) {
        workspaceRepository.delete(findOwnedWorkspace(workspaceId, currentUserId));
    }

    private Workspace findOwnedWorkspace(Long workspaceId, Long currentUserId) {
        return workspaceRepository.findByIdAndOwnerId(workspaceId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
    }

    private WorkspaceResponse toResponse(Workspace workspace) {
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), workspace.getDescription(), workspace.getCreatedAt(), workspace.getUpdatedAt());
    }
}
