package com.studyhub.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.studyhub.workspace.Workspace;

public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course>{
    Page<Course> findAllByWorkspaceOwnerId(Long ownerId, Pageable pageable);
    Optional<Course> findByIdAndWorkspaceOwnerId(Long id, Long ownerId);
}