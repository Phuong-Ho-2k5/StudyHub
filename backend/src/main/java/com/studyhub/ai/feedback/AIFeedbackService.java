package com.studyhub.ai.feedback;

import com.studyhub.ai.*;
import com.studyhub.course.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AIFeedbackService {
    private final FeedbackMessageScopeRepository scope;
    private final CourseRepository courses;
    private final ChatSessionRepository sessions;
    private final AIFeedbackRepository feedback;
    public AIFeedbackService(FeedbackMessageScopeRepository scope,CourseRepository courses,ChatSessionRepository sessions,AIFeedbackRepository feedback) {
        this.scope=scope;this.courses=courses;this.sessions=sessions;this.feedback=feedback;
    }
    public record SaveFeedbackResult(FeedbackResponse feedback,boolean created) {}
    @Transactional
    public SaveFeedbackResult save(long messageId,long userId,FeedbackRequest request) {
        var initial=scope.findOwnedScope(messageId,userId).orElseThrow(AIFeedbackService::missing);
        if(!scope.lockUser(userId))throw missing();
        courses.lockOwnedCourse(initial.courseId(),userId).orElseThrow(AIFeedbackService::missing);
        sessions.lockOwnedSession(initial.sessionId(),initial.courseId(),userId).orElseThrow(AIFeedbackService::missing);
        var current=scope.findOwnedScopeInSession(messageId,initial.sessionId(),initial.courseId(),userId).orElseThrow(AIFeedbackService::missing);
        if(current.role()!=ChatMessageRole.ASSISTANT)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only saved assistant messages accept feedback.");
        var existing=feedback.findByUserIdAndMessageId(userId,messageId);
        var row=existing.orElseGet(()->new AIFeedback(userId,messageId,request.getRating(),request.getNote()));
        row.replace(request.getRating(),request.getNote());
        feedback.saveAndFlush(row);
        return new SaveFeedbackResult(FeedbackResponse.from(row),existing.isEmpty());
    }
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Chat message not found");}
}
