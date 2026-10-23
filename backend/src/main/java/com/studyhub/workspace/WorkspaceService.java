package com.studyhub.workspace;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyhub.security.ResourceAccessService;
import com.studyhub.user.User;
import com.studyhub.user.UserRepository;
import com.studyhub.workspace.dto.CreateWorkspaceRequest;
import com.studyhub.workspace.dto.UpdateWorkspaceRequest;
import com.studyhub.workspace.dto.WorkspaceResponse;
import com.studyhub.document.storage.DocumentFileLifecycle;

@Service
public class WorkspaceService {
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final ResourceAccessService resourceAccessService;
    private final DocumentFileLifecycle files;

    public WorkspaceService(WorkspaceRepository workspaceRepository, UserRepository userRepository,
            ResourceAccessService resourceAccessService, DocumentFileLifecycle files) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.resourceAccessService = resourceAccessService;
        this.files = files;
    }

    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, Long currentUserId) {
        User owner = userRepository.getReferenceById(currentUserId);
        Workspace workspace = new Workspace(request.name(), request.description(), owner);
        return toResponse(workspaceRepository.save(workspace));
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(Long workspaceId, Long currentUserId) {
        return toResponse(resourceAccessService.requireWorkspace(workspaceId, currentUserId));
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getAllWorkspaces(Long currentUserId) {
        return workspaceRepository.findAllByOwnerId(currentUserId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(Long workspaceId, UpdateWorkspaceRequest request, Long currentUserId) {
        Workspace workspace = resourceAccessService.requireWorkspace(workspaceId, currentUserId);
        workspace.setName(request.name());
        workspace.setDescription(request.description());
        workspaceRepository.flush();
        return toResponse(workspace);
    }

    @Transactional
    public void deleteWorkspace(Long workspaceId, Long currentUserId) {
        Workspace workspace = resourceAccessService.requireWorkspace(workspaceId, currentUserId);
        files.deleteWorkspace(workspaceId);
        workspaceRepository.delete(workspace);
    }


    private WorkspaceResponse toResponse(Workspace workspace) {
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), workspace.getDescription(), workspace.getCreatedAt(), workspace.getUpdatedAt());
    }
}
