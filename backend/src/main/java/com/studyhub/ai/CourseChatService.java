package com.studyhub.ai;

import com.studyhub.ai.dto.*;
import com.studyhub.search.CourseSearchService;
import com.studyhub.search.dto.CourseSearchRequest;
import com.studyhub.security.ResourceAccessService;
import org.springframework.stereotype.Service;

@Service
public class CourseChatService {
    public static final String PROMPT_VERSION = "course-rag-v1";
    public static final String INSUFFICIENT_ANSWER = "Không đủ thông tin trong tài liệu của khóa học để trả lời câu hỏi này.";
    private final CourseSearchService search;
    private final ResourceAccessService access;
    private final RagAnswerClient ai;

    public CourseChatService(CourseSearchService search, ResourceAccessService access, RagAnswerClient ai) {
        this.search = search; this.access = access; this.ai = ai;
    }

    public CourseChatResponse chat(Long courseId, Long userId, CourseChatRequest request) {
        String question = request.getQuestion().strip();
        var query = new CourseSearchRequest(); query.setQuery(question); query.setTopK(request.getTopK());
        // Search authorizes before embedding, and filters ownership/published versions in SQL.
        var retrieved = search.search(courseId, userId, query);
        var result = retrieved.items().isEmpty() ? null : ai.answer(question, retrieved.items());
        // No transaction spans HTTP. Recheck after the potentially slow generation call.
        access.requireCourse(courseId, userId);
        boolean insufficient = result == null || "INSUFFICIENT_CONTEXT".equals(result.status());
        return new CourseChatResponse(courseId, question, insufficient ? "INSUFFICIENT_CONTEXT" : "ANSWERED",
                insufficient ? INSUFFICIENT_ANSWER : result.answer(), result == null ? null : result.model(), PROMPT_VERSION);
    }
}
