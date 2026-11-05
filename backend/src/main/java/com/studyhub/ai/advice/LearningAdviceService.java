package com.studyhub.ai.advice;

import static com.studyhub.ai.advice.LearningAdviceModels.*;
import com.studyhub.ai.CitationMapper;
import com.studyhub.search.CourseSearchService;
import com.studyhub.search.dto.CourseSearchRequest;
import com.studyhub.security.ResourceAccessService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LearningAdviceService {
    private final LearningContextService context;
    private final CourseSearchService search;
    private final ResourceAccessService access;
    private final LearningAdviceClient ai;
    private final CitationMapper citations;

    public LearningAdviceService(LearningContextService context, CourseSearchService search, ResourceAccessService access,
                                 LearningAdviceClient ai, CitationMapper citations) {
        this.context=context;this.search=search;this.access=access;this.ai=ai;this.citations=citations;
    }

    public AdviceResponse advise(Long courseId,Long userId,AdviceRequest request) {
        var snapshot=context.build(courseId,userId);
        if (!snapshot.hasLearningData()) return new AdviceResponse(courseId,request.question(),"INSUFFICIENT_CONTEXT",INSUFFICIENT_ANSWER,
            snapshot.asOf().toString(),null,PROMPT_VERSION,List.of(),List.of(),snapshot.limitations());
        String query=request.question()+(snapshot.retrievalHint().isBlank()?"":"\n"+snapshot.retrievalHint());
        // Public question and three bounded Concept names keep this below the search query limit.
        var searchRequest=new CourseSearchRequest();searchRequest.setQuery(query);searchRequest.setTopK(request.topK());
        var retrieved=search.search(courseId,userId,searchRequest);
        var result=ai.generate(request.question(),snapshot.facts(),retrieved.items());
        access.requireCourse(courseId,userId);
        validateFacts(result,snapshot);
        var mapped=result.evidence().isEmpty()?List.<com.studyhub.ai.dto.CourseCitationResponse>of():
            citations.map(courseId,userId,retrieved.items(),result.evidence());
        var used=new HashSet<>(result.usedLearningEvidenceIds());
        var facts=snapshot.facts().stream().filter(f -> used.contains(f.id())).toList();
        var limitations=new ArrayList<>(snapshot.limitations());
        if(retrieved.items().isEmpty()) limitations.add("NO_RETRIEVED_DOCUMENTS");
        return new AdviceResponse(courseId,request.question(),result.status(),
            result.status().equals("INSUFFICIENT_CONTEXT")?INSUFFICIENT_ANSWER:result.answer(),snapshot.asOf().toString(),
            result.model(),PROMPT_VERSION,facts,mapped,List.copyOf(limitations));
    }

    private static void validateFacts(AdviceGenerationResult result,LearningSnapshot snapshot) {
        var known=new HashSet<>(snapshot.facts().stream().map(LearningFact::id).toList());
        var used=result.usedLearningEvidenceIds();
        if(new HashSet<>(used).size()!=used.size() || !known.containsAll(used)) throw invalid();
        boolean valid=switch(result.status()) {
            case "ANSWERED" -> !used.isEmpty() && !result.evidence().isEmpty();
            case "LEARNING_DATA_ONLY" -> !used.isEmpty() && result.evidence().isEmpty();
            case "INSUFFICIENT_CONTEXT" -> used.isEmpty() && result.evidence().isEmpty();
            default -> false;
        };
        if(!valid) throw invalid();
    }

    static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI learning advice returned invalid output."); }
}
