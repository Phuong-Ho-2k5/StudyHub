package com.studyhub.ai.quiz;

import static com.studyhub.ai.quiz.QuizGenerationModels.*;
import static com.studyhub.ai.concept.ConceptSuggestionModels.normalized;
import com.studyhub.ai.concept.ConceptSuggestionModels;
import com.studyhub.ai.dto.CourseCitationResponse;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class QuizGenerationEvidenceValidator {
    public ValidatedResult validate(SourceWindow window,GeneratedResult result,int count) {
        try {
            if(result==null || !PROMPT_VERSION.equals(result.promptVersion()) || result.questions().size()!=count) throw invalid();
            String model=normalized(result.model(),200);
            var sources=window.contexts().stream().collect(Collectors.toMap(ConceptSuggestionModels.Context::chunkId,Function.identity()));
            var questions=new ArrayList<ValidatedQuestion>();var texts=new HashSet<String>();
            for(var question:result.questions()) {
                String text=normalized(question.text(),500);
                if(!texts.add(fold(text)) || question.options().size()!=4 || question.options().stream().filter(Option::correct).count()!=1
                    || question.evidence().isEmpty() || question.evidence().size()>3) throw invalid();
                var options=new ArrayList<Option>();var optionTexts=new HashSet<String>();
                for(var option:question.options()) {
                    String value=normalized(option.text(),500);if(!optionTexts.add(fold(value))) throw invalid();
                    options.add(new Option(value,option.correct()));
                }
                var citations=new ArrayList<CourseCitationResponse>();var seen=new HashSet<Long>();
                for(var e:question.evidence()) {
                    var context=sources.get(e.chunkId());
                    if(context==null || !seen.add(e.chunkId()) || e.quote()==null || e.quote().codePointCount(0,e.quote().length())>1000
                        || e.quote().codePoints().allMatch(ConceptSuggestionModels::whitespace) || !context.content().contains(e.quote())) throw invalid();
                    citations.add(new CourseCitationResponse(context.chunkId(),context.documentId(),context.documentTitle(),context.chunkIndex(),context.pageStart(),context.pageEnd(),e.quote(),
                        "/api/courses/"+window.courseId()+"/documents/"+context.documentId()+"/chunks/"+context.chunkId()+"/source",context.managedFile()?"/api/documents/"+context.documentId()+"/file":null));
                }
                questions.add(new ValidatedQuestion(text,List.copyOf(options),List.copyOf(citations)));
            }
            return new ValidatedResult(model,PROMPT_VERSION,List.copyOf(questions));
        } catch(IllegalArgumentException | NullPointerException error) {throw invalid();}
    }
    // Upper then lower handles common full case folds (e.g. sharp s) consistently with Python casefold.
    static String fold(String value) {return value.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);}
    static ResponseStatusException invalid() {return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI returned invalid quiz output.");}
}
