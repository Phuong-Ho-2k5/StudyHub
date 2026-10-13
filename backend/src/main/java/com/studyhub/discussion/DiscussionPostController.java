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
import com.studyhub.discussion.dto.DiscussionPostRequest;
import com.studyhub.discussion.dto.DiscussionPostResponse;
import com.studyhub.security.CustomUserDetails;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class DiscussionPostController {
    private final DiscussionPostService service;

    public DiscussionPostController(DiscussionPostService service) {
        this.service = service;
    }

    @PostMapping("/courses/{courseId}/discussion-posts")
    public ResponseEntity<DiscussionPostResponse> create(@PathVariable Long courseId,
            @Valid @RequestBody DiscussionPostRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(courseId, request, user.getId()));
    }

    @GetMapping("/courses/{courseId}/discussion-posts")
    public Page<DiscussionPostResponse> list(@PathVariable Long courseId,
            @AuthenticationPrincipal CustomUserDetails user,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(courseId, user.getId(), pageable);
    }

    @GetMapping("/discussion-posts/{id}")
    public DiscussionPostResponse get(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.get(id, user.getId());
    }

    @PutMapping("/discussion-posts/{id}")
    public DiscussionPostResponse update(@PathVariable Long id,
            @Valid @RequestBody DiscussionPostRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return service.update(id, request, user.getId());
    }

    @DeleteMapping("/discussion-posts/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        service.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
