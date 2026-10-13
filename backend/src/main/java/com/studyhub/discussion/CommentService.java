package com.studyhub.discussion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.studyhub.discussion.dto.CommentRequest;
import com.studyhub.discussion.dto.CommentResponse;
import com.studyhub.security.ResourceAccessService;
import com.studyhub.user.User;
import com.studyhub.user.UserRepository;

@Service
public class CommentService {
    private final CommentRepository commentRepository;
    private final DiscussionPostRepository postRepository;
    private final UserRepository userRepository;
    private final ResourceAccessService resourceAccessService;

    public CommentService(CommentRepository commentRepository, DiscussionPostRepository postRepository,
            UserRepository userRepository, ResourceAccessService resourceAccessService) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.resourceAccessService = resourceAccessService;
    }

    @Transactional
    public CommentResponse create(Long postId, CommentRequest request, Long currentUserId) {
        DiscussionPost post = requireAccessiblePost(postId, currentUserId);
        User author = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        Comment comment = new Comment(post, author, request.content().trim());
        return toResponse(commentRepository.saveAndFlush(comment));
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> list(Long postId, Long currentUserId, Pageable pageable) {
        requireAccessiblePost(postId, currentUserId);
        return commentRepository.findByPostId(postId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CommentResponse get(Long commentId, Long currentUserId) {
        return toResponse(requireAccessibleComment(commentId, currentUserId));
    }

    @Transactional
    public CommentResponse update(Long commentId, CommentRequest request, Long currentUserId) {
        Comment comment = requireAccessibleComment(commentId, currentUserId);
        requireAuthor(comment, currentUserId);
        comment.updateContent(request.content().trim());
        commentRepository.flush();
        return toResponse(comment);
    }

    @Transactional
    public void delete(Long commentId, Long currentUserId) {
        Comment comment = requireAccessibleComment(commentId, currentUserId);
        requireAuthor(comment, currentUserId);
        commentRepository.delete(comment);
    }

    private DiscussionPost requireAccessiblePost(Long postId, Long currentUserId) {
        DiscussionPost post = postRepository.findById(postId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Discussion post not found"));
        resourceAccessService.requireCourse(post.getCourse().getId(), currentUserId);
        return post;
    }

    private Comment requireAccessibleComment(Long commentId, Long currentUserId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(this::notFound);
        resourceAccessService.requireCourse(comment.getPost().getCourse().getId(), currentUserId);
        return comment;
    }

    private void requireAuthor(Comment comment, Long currentUserId) {
        if (!comment.getAuthor().getId().equals(currentUserId)) throw notFound();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(comment.getId(), comment.getPost().getId(), comment.getAuthor().getId(),
                comment.getContent(), comment.getCreatedAt(), comment.getUpdatedAt());
    }
}
