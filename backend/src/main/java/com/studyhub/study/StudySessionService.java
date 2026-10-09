package com.studyhub.study;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.studyhub.course.Course;
import com.studyhub.security.ResourceAccessService;
import com.studyhub.study.dto.StartStudySessionRequest;
import com.studyhub.study.dto.StudySessionResponse;
import com.studyhub.user.User;
import com.studyhub.user.UserRepository;

@Service
public class StudySessionService {
    private final StudySessionRepository repository;
    private final UserRepository userRepository;
    private final ResourceAccessService resourceAccessService;

    public StudySessionService(StudySessionRepository repository, UserRepository userRepository,
            ResourceAccessService resourceAccessService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.resourceAccessService = resourceAccessService;
    }

    @Transactional
    public StudySessionResponse start(StartStudySessionRequest request, Long currentUserId) {
        Course course = resourceAccessService.requireCourse(request.courseId(), currentUserId);
        User user = userRepository.getReferenceById(currentUserId);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return toResponse(repository.save(new StudySession(user, course, now)));
    }

    @Transactional
    public StudySessionResponse finish(Long sessionId, Long currentUserId) {
        StudySession session = repository.findOwnedForUpdate(sessionId, currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study session not found"));
        if (session.getEndTime() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Study session already finished");
        }
        session.endSession(Instant.now().truncatedTo(ChronoUnit.MICROS));
        repository.flush();
        return toResponse(session);
    }

    private StudySessionResponse toResponse(StudySession session) {
        return new StudySessionResponse(session.getId(), session.getUser().getId(),session.getCourse().getId(), session.getStartTime(), session.getEndTime(), session.getDuration());
    }
}
