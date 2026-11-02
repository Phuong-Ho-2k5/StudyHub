package com.studyhub.ai.concept;

import static com.studyhub.ai.concept.ConceptSuggestionModels.*;
import com.studyhub.ai.dto.CourseCitationResponse;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ConceptEvidenceValidator {
    public ValidatedExtraction validate(SourceWindow window, ExtractionResult result, int maxSuggestions) {
        try {
            if(result==null || !PROMPT_VERSION.equals(result.promptVersion()) || result.model()==null
                || normalized(result.model(),200).isEmpty() || result.suggestions()==null
                || result.suggestions().size()>maxSuggestions) throw invalid();
            var sources=window.contexts().stream().collect(Collectors.toMap(Context::chunkId,Function.identity()));
            var names=new HashSet<String>(); var output=new ArrayList<ValidatedSuggestion>();
            for(var suggestion:result.suggestions()) {
                String name=normalized(suggestion.name(),150), description=normalized(suggestion.description(),1000);
                if(!names.add(name) || suggestion.evidence()==null || suggestion.evidence().isEmpty()
                    || suggestion.evidence().size()>3) throw invalid();
                var seen=new HashSet<Long>();var citations=new ArrayList<CourseCitationResponse>();
                for(var evidence:suggestion.evidence()) {
                    var context=sources.get(evidence.chunkId());
                    if(context==null || !window.documentId().equals(context.documentId())
                        || !seen.add(evidence.chunkId()) || evidence.quote()==null
                        || evidence.quote().codePointCount(0,evidence.quote().length())>1000
                        || evidence.quote().codePoints().allMatch(ConceptSuggestionModels::whitespace)
                        || !context.content().contains(evidence.quote())) throw invalid();
                    citations.add(new CourseCitationResponse(context.chunkId(),context.documentId(),context.documentTitle(),
                        context.chunkIndex(),context.pageStart(),context.pageEnd(),evidence.quote(),
                        "/api/courses/"+window.courseId()+"/documents/"+context.documentId()+"/chunks/"+context.chunkId()+"/source",
                        context.managedFile()?"/api/documents/"+context.documentId()+"/file":null));
                }
                output.add(new ValidatedSuggestion(name,description,List.copyOf(citations)));
            }
            return new ValidatedExtraction(result.model(),result.promptVersion(),List.copyOf(output));
        } catch(IllegalArgumentException | NullPointerException error) { throw invalid(); }
    }
    static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI returned invalid Concept suggestions.");
    }
}
