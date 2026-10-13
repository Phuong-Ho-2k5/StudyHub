package com.studyhub.discussion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.studyhub.course.Course;
import com.studyhub.discussion.dto.DiscussionPostRequest;
import com.studyhub.discussion.dto.DiscussionPostResponse;
import com.studyhub.security.ResourceAccessService;
import com.studyhub.user.User;
import com.studyhub.user.UserRepository;

@Service
public class DiscussionPostService {
    private final DiscussionPostRepository postRepository;
    private final UserRepository userRepository;
    private final ResourceAccessService resourceAccessService;

    public DiscussionPostService(DiscussionPostRepository postRepository,
            UserRepository userRepository, ResourceAccessService resourceAccessService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.resourceAccessService = resourceAccessService;
    }

    @Transactional
    public DiscussionPostResponse create(Long courseId, DiscussionPostRequest request, Long currentUserId) {
        Course course = resourceAccessService.requireCourse(courseId, currentUserId);
        User author = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        DiscussionPost post = new DiscussionPost(course, author, request.title().trim(), request.content().trim());
        return toResponse(postRepository.saveAndFlush(post));
    }

    @Transactional(readOnly = true)
    public Page<DiscussionPostResponse> list(Long courseId, Long currentUserId, Pageable pageable) {
        resourceAccessService.requireCourse(courseId, currentUserId);
        return postRepository.findByCourseId(courseId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DiscussionPostResponse get(Long postId, Long currentUserId) {
        return toResponse(requireAccessiblePost(postId, currentUserId));
    }

    @Transactional
    public DiscussionPostResponse update(Long postId, DiscussionPostRequest request, Long currentUserId) {
        DiscussionPost post = requireAccessiblePost(postId, currentUserId);
        requireAuthor(post, currentUserId);
        post.updateContent(request.title().trim(), request.content().trim());
        postRepository.flush();
        return toResponse(post);
    }

    @Transactional
    public void delete(Long postId, Long currentUserId) {
        DiscussionPost post = requireAccessiblePost(postId, currentUserId);
        requireAuthor(post, currentUserId);
        postRepository.delete(post);
    }

    private DiscussionPost requireAccessiblePost(Long postId, Long currentUserId) {
        DiscussionPost post = postRepository.findById(postId).orElseThrow(this::notFound);
        resourceAccessService.requireCourse(post.getCourse().getId(), currentUserId);
        return post;
    }

    private void requireAuthor(DiscussionPost post, Long currentUserId) {
        if (!post.getAuthor().getId().equals(currentUserId)) throw notFound();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Discussion post not found");
    }

    private DiscussionPostResponse toResponse(DiscussionPost post) {
        return new DiscussionPostResponse(post.getId(), post.getCourse().getId(), post.getAuthor().getId(),
                post.getTitle(), post.getContent(), post.getCreatedAt(), post.getUpdatedAt());
    }
}
