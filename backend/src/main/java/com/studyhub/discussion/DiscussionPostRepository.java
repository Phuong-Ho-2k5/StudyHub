package com.studyhub.discussion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionPostRepository extends JpaRepository<DiscussionPost, Long> {
    Page<DiscussionPost> findByCourseId(Long courseId, Pageable pageable);
}
