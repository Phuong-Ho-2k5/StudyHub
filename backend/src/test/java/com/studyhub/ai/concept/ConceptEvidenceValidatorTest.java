package com.studyhub.ai.concept;

import static org.junit.jupiter.api.Assertions.*;
import static com.studyhub.ai.concept.ConceptSuggestionModels.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ConceptEvidenceValidatorTest {
    final ConceptEvidenceValidator validator=new ConceptEvidenceValidator();
    final SourceWindow window=new SourceWindow(1L,2L,"v",0,List.of(new Context(3L,2L,"DB",0,"Nguyên tử Atomicity",null,null,true)),null);
    ExtractionResult output(GeneratedSuggestion... items) { return new ExtractionResult("model",PROMPT_VERSION,List.of(items)); }
    GeneratedSuggestion item(String name,List<Evidence> evidence) { return new GeneratedSuggestion(name,"Nguyên tử",evidence); }
    @Test void validatesUnicodeAndDerivesSourceMetadata() {
        var result=validator.validate(window,output(item("\u00a0Atomicity\u0085",List.of(new Evidence(3L,"Nguyên tử")))),5);
        assertEquals("Atomicity",result.suggestions().getFirst().name());
        var citation=result.suggestions().getFirst().citations().getFirst();
        assertEquals("/api/courses/1/documents/2/chunks/3/source",citation.sourceUrl());
        assertEquals("/api/documents/2/file",citation.fileUrl());assertNull(citation.pageStart());
    }
    @Test void rejectsInvalidEvidenceAndDuplicateNamesWithoutPartialSuccess() {
        var valid=item("Atomicity",List.of(new Evidence(3L,"Atomicity")));
        for(var result:List.of(output(item("X",List.of())),output(item("X",List.of(new Evidence(4L,"Atomicity")))),
            output(item("X",List.of(new Evidence(3L,"invented")))),output(item("X",List.of(new Evidence(3L,"\u00a0")))),
            output(item("X",List.of(new Evidence(3L,"Atomicity"),new Evidence(3L,"Atomicity")))),
            output(valid,item(" Atomicity ",List.of(new Evidence(3L,"Atomicity"))))))
            assertEquals(502,assertThrows(ResponseStatusException.class,()->validator.validate(window,result,5)).getStatusCode().value());
        var foreign=new SourceWindow(1L,9L,"v",0,window.contexts(),null);
        assertThrows(ResponseStatusException.class,()->validator.validate(foreign,output(valid),5));
    }
}
