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

    // Lock only the Course row; joined ownership tables must not be locked during append.
    @org.springframework.data.jpa.repository.Query(value="""
        SELECT id FROM courses WHERE id=:courseId
        AND workspace_id IN (SELECT id FROM workspaces WHERE owner_id=:userId) FOR UPDATE
        """, nativeQuery=true)
    Optional<Long> lockOwnedCourse(@org.springframework.data.repository.query.Param("courseId") Long courseId,
            @org.springframework.data.repository.query.Param("userId") Long userId);
}
