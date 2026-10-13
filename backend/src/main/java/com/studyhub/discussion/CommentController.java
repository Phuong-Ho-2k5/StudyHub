package com.studyhub.discussion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import com.studyhub.discussion.dto.CommentRequest;
import com.studyhub.discussion.dto.CommentResponse;
import com.studyhub.security.CustomUserDetails;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class CommentController {
    private final CommentService service;

    public CommentController(CommentService service) { this.service = service; }

    @PostMapping("/discussion-posts/{postId}/comments")
    public ResponseEntity<CommentResponse> create(@PathVariable Long postId,
            @Valid @RequestBody CommentRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(postId, request, user.getId()));
    }

    @GetMapping("/discussion-posts/{postId}/comments")
    public Page<CommentResponse> list(@PathVariable Long postId, @AuthenticationPrincipal CustomUserDetails user,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.ASC)
            Pageable pageable) {
        return service.list(postId, user.getId(), pageable);
    }

    @GetMapping("/comments/{id}")
    public CommentResponse get(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.get(id, user.getId());
    }

    @PutMapping("/comments/{id}")
    public CommentResponse update(@PathVariable Long id, @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return service.update(id, request, user.getId());
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        service.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
