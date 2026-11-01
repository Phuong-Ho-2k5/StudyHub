package com.studyhub.ai;

import com.studyhub.ai.dto.*;
import com.studyhub.security.ResourceAccessService;
import com.studyhub.course.CourseRepository;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChatHistoryService {
    private static final TypeReference<List<CourseCitationResponse>> CITATIONS = new TypeReference<>() {};
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final ResourceAccessService access;
    private final ObjectMapper json;
    private final CourseRepository courses;

    public ChatHistoryService(ChatSessionRepository sessions, ChatMessageRepository messages,
            ResourceAccessService access, ObjectMapper json, CourseRepository courses) {
        this.sessions=sessions; this.messages=messages; this.access=access; this.json=json;
        this.courses=courses;
    }

    @Transactional
    public ChatSessionResponse createSession(Long courseId, Long userId, CreateChatSessionRequest request) {
        lockCourse(courseId,userId);
        return toResponse(sessions.saveAndFlush(new ChatSession(userId,courseId,request.getTitle())));
    }

    @Transactional(readOnly=true)
    public Page<ChatSessionResponse> listSessions(Long courseId, Long userId, Pageable page) {
        access.requireCourse(courseId,userId);
        return sessions.findByCourseIdAndUserId(courseId,userId,page).map(this::toResponse);
    }

    @Transactional(readOnly=true)
    public Page<ChatMessageResponse> listMessages(Long courseId, Long userId, Long sessionId, Pageable page) {
        requireSession(courseId,userId,sessionId);
        return messages.findBySessionId(sessionId,page).map(message -> new ChatMessageResponse(
            message.getId(),message.getSessionId(),message.getRole(),message.getContent(),message.getStatus(),
            message.getModel(),message.getPromptVersion(),json.readValue(message.getCitationsJson(),CITATIONS),message.getCreatedAt()));
    }

    @Transactional(readOnly=true)
    public ChatSession requireSession(Long courseId, Long userId, Long sessionId) {
        access.requireCourse(courseId,userId);
        return sessions.findByIdAndCourseIdAndUserId(sessionId,courseId,userId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Chat session not found"));
    }

    public record SavedTurn(Long sessionId, Long userMessageId, Long assistantMessageId) {}

    /** Called through the Spring proxy only after RAG HTTP and citation validation finish. */
    @Transactional
    public SavedTurn appendTurn(Long courseId, Long userId, Long sessionId, CourseChatResponse result) {
        lockCourse(courseId,userId);
        var session=sessions.lockOwnedSession(sessionId,courseId,userId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Chat session not found"));
        String snapshot=json.writeValueAsString(result.citations());
        var user=messages.saveAndFlush(new ChatMessage(sessionId,ChatMessageRole.USER,result.question(),null,null,null,"[]"));
        var assistant=messages.saveAndFlush(new ChatMessage(sessionId,ChatMessageRole.ASSISTANT,result.answer(),
            result.status(),result.model(),result.promptVersion(),snapshot));
        session.touch();
        return new SavedTurn(sessionId,user.getId(),assistant.getId());
    }

    private void lockCourse(Long courseId, Long userId) {
        courses.lockOwnedCourse(courseId,userId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Course not found"));
    }

    private ChatSessionResponse toResponse(ChatSession session) {
        return new ChatSessionResponse(session.getId(),session.getCourseId(),session.getTitle(),session.getCreatedAt(),session.getUpdatedAt());
    }
}
