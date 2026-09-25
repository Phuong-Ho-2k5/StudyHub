package com.studyhub.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    List<Workspace> findAllByOwnerId(Long ownerId);
    Optional<Workspace> findByIdAndOwnerId(Long workspaceId, Long ownerId);
}
