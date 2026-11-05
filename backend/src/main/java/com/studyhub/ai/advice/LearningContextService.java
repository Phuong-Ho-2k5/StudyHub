package com.studyhub.ai.advice;

import static com.studyhub.ai.advice.LearningAdviceModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyhub.course.CourseProgressService;
import com.studyhub.security.ResourceAccessService;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningContextService {
    private final ResourceAccessService access;
    private final CourseProgressService progress;
    private final LearningContextRepository repository;
    private final ObjectMapper json = new ObjectMapper();

    public LearningContextService(ResourceAccessService access, CourseProgressService progress, LearningContextRepository repository) {
        this.access = access; this.progress = progress; this.repository = repository;
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public LearningSnapshot build(Long courseId, Long userId) {
        access.requireCourse(courseId,userId);
        Instant asOf = Instant.now();
        var overall = progress.getProgress(courseId,userId);
        var candidates = repository.concepts(courseId,userId);
        var concepts = candidates.stream().limit(10).toList();
        var edges = repository.prerequisites(courseId,userId,concepts.stream().map(LearningContextRepository.ConceptRow::id).toList());
        var attempts = repository.attempts(courseId,userId);
        var study = repository.study(courseId,userId);
        var facts = new ArrayList<LearningFact>(); var limitations = new ArrayList<String>();
        facts.add(fact("progress","PROGRESS", overall));
        for (var c : concepts) facts.add(fact("concept:"+c.id(),"CONCEPT",Map.of("conceptId",c.id(),"name",name(c.name()),"confidence",c.confidence())));
        for (var edge : edges.stream().limit(20).toList()) facts.add(fact("prerequisite:"+edge.dependentId()+":"+edge.prerequisiteId(),"PREREQUISITE",Map.of(
            "dependentConceptId",edge.dependentId(),"dependentName",name(edge.dependentName()),"dependentConfidence",edge.dependentConfidence(),
            "prerequisiteConceptId",edge.prerequisiteId(),"prerequisiteName",name(edge.prerequisiteName()),"prerequisiteConfidence",edge.prerequisiteConfidence())));
        for (var attempt : attempts) facts.add(fact("attempt:"+attempt.id(),"QUIZ_ATTEMPT",attempt));
        facts.add(fact("study-summary","STUDY_SUMMARY",study));
        if (overall.totalConcepts()==0) limitations.add("NO_CONCEPTS");
        if (attempts.isEmpty()) limitations.add("NO_QUIZ_ATTEMPTS");
        if (study.completedStudySessions()==0) limitations.add("NO_COMPLETED_STUDY_SESSIONS");
        if (candidates.size()>10) limitations.add("CONCEPT_SELECTION_LIMIT");
        if (edges.size()>20) limitations.add("PREREQUISITE_SELECTION_LIMIT");
        String hint = String.join("; ", concepts.stream().filter(c -> c.confidence()<50).limit(3).map(c -> name(c.name())).toList());
        return new LearningSnapshot(asOf, overall.totalConcepts()>0 || !attempts.isEmpty() || study.completedStudySessions()>0,
            List.copyOf(facts),List.copyOf(limitations),hint);
    }

    private LearningFact fact(String id, String type, Object data) {
        return new LearningFact(id,type,json.convertValue(data, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {}));
    }
    private static String name(String value) { return value.substring(0, value.offsetByCodePoints(0,Math.min(200,value.codePointCount(0,value.length())))); }
}
