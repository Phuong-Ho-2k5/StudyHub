package com.studyhub.study;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studyhub.security.CustomUserDetails;
import com.studyhub.study.dto.StartStudySessionRequest;
import com.studyhub.study.dto.StudySessionResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/study-sessions")
public class StudySessionController {
    private final StudySessionService service;

    public StudySessionController(StudySessionService service) {
        this.service = service;
    }

    @PostMapping("/start")
    public ResponseEntity<StudySessionResponse> start(
            @Valid @RequestBody StartStudySessionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.start(request, currentUser.getId()));
    }

    @PostMapping("/{id}/finish")
    public StudySessionResponse finish(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return service.finish(id, currentUser.getId());
    }
}
