package com.studyhub.ai.quiz;

import static org.junit.jupiter.api.Assertions.*;
import static com.studyhub.ai.quiz.QuizGenerationModels.*;
import com.studyhub.ai.concept.ConceptSuggestionModels.Context;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class QuizGenerationValidationTest {
    final SourceWindow window=new SourceWindow(1L,List.of(new DocumentCoverage(11L,"version",List.of(1L))),List.of(new Context(1L,11L,"Doc",0,"Atomicity",1,1,false)));
    final List<Option> options=List.of(new Option("A",true),new Option("B",false),new Option("C",false),new Option("D",false));
    final QuizGenerationEvidenceValidator validator=new QuizGenerationEvidenceValidator();
    GeneratedQuestion question(String text,List<Option> options,List<Evidence> evidence) {return new GeneratedQuestion(text,options,evidence);}
    @Test void rejectsInvalidOptionsDuplicatesMissingCountsAndForeignOrFabricatedEvidence() {
        var valid=question("Question",options,List.of(new Evidence(1L,"Atomicity")));
        var badQuestions=List.of(question(" ",options,valid.evidence()),question("x".repeat(501),options,valid.evidence()),
            question("Question",List.of(new Option("A",true),new Option("a",false),new Option("C",false),new Option("D",false)),valid.evidence()),
            question("Question",options,List.of(new Evidence(99L,"Atomicity"))),question("Question",options,List.of(new Evidence(1L,"Fabricated"))),
            question("Question",options,List.of(new Evidence(1L,"Atomicity"),new Evidence(1L,"Atomicity"))),
            question("Question",List.of(new Option("A",true),new Option("B",true),new Option("C",false),new Option("D",false)),valid.evidence()));
        for(var bad:badQuestions) assertEquals(502,assertThrows(ResponseStatusException.class,()->validator.validate(window,new GeneratedResult("model",PROMPT_VERSION,List.of(bad)),1)).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->validator.validate(window,new GeneratedResult("model",PROMPT_VERSION,List.of(valid)),2));
        assertThrows(ResponseStatusException.class,()->validator.validate(window,new GeneratedResult("model",PROMPT_VERSION,List.of(valid,question(" QUESTION ",options,valid.evidence()))),2));
        assertEquals("/api/courses/1/documents/11/chunks/1/source",validator.validate(window,new GeneratedResult("model",PROMPT_VERSION,List.of(valid)),1).questions().getFirst().citations().getFirst().sourceUrl());
    }
}
