package com.studyhub.concept.recommendation;

import static com.studyhub.concept.recommendation.NextConceptModels.*;

import com.studyhub.ai.advice.LearningAdviceModels.LearningFact;
import com.studyhub.ai.advice.LearningContextRepository;
import com.studyhub.security.ResourceAccessService;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NextConceptService {
    private final ResourceAccessService access;
    private final NextConceptRepository repository;
    private final NextConceptEngine engine;
    private final LearningContextRepository learning;
    private final Clock clock;

    @Autowired
    public NextConceptService(ResourceAccessService access, NextConceptRepository repository,
            NextConceptEngine engine, LearningContextRepository learning) {
        this(access,repository,engine,learning,Clock.systemUTC());
    }

    NextConceptService(ResourceAccessService access, NextConceptRepository repository,
            NextConceptEngine engine, LearningContextRepository learning, Clock clock) {
        this.access=access; this.repository=repository; this.engine=engine; this.learning=learning; this.clock=clock;
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public NextConceptResponse recommend(long courseId, long userId, int limit) {
        var asOf=clock.instant();
        access.requireCourse(courseId,userId);
        var ranking=engine.rank(repository.loadGraph(courseId,userId),limit);
        var attempts=learning.attempts(courseId,userId);
        var study=learning.study(courseId,userId);
        var facts=new ArrayList<LearningFact>();
        var items=new ArrayList<NextConceptRecommendation>();
        for (var ranked : ranking.concepts()) {
            var concept=ranked.concept(); String evidenceId="concept:"+concept.id();
            var reasons=new ArrayList<String>();
            reasons.add(concept.confidence()<50 ? "LOW_CONFIDENCE" : "DEVELOPING_CONFIDENCE");
            if(ranked.unlocksCount()>0) reasons.add("FOUNDATION_FOR_OTHER_CONCEPTS");
            items.add(new NextConceptRecommendation(items.size()+1,concept.id(),concept.name(),concept.confidence(),
                concept.confidence()<50 ? "REVIEW" : "CONTINUE",List.copyOf(reasons),ranked.unlocksCount(),List.of(evidenceId)));
            facts.add(new LearningFact(evidenceId,"CONCEPT_CONFIDENCE",Map.of("conceptId",concept.id(),
                "confidence",concept.confidence(),"readinessThreshold",READINESS_THRESHOLD,
                "prerequisitesSatisfied",true,"unlocksCount",ranked.unlocksCount())));
        }
        for(var attempt : attempts) facts.add(new LearningFact("attempt:"+attempt.id(),"QUIZ_ATTEMPT_SUMMARY",Map.of(
            "id",attempt.id(),"quizId",attempt.quizId(),"questionPercentage",attempt.questionPercentage(),
            "correctCount",attempt.correctCount(),"totalQuestions",attempt.totalQuestions(),"createdAt",attempt.createdAt())));
        // Null lastCompletedAt is meaningful for a Course without completed sessions.
        var summary=new LinkedHashMap<String,Object>();
        summary.put("completedStudySessions",study.completedStudySessions());
        summary.put("totalStudySeconds",study.totalStudySeconds()); summary.put("lastCompletedAt",study.lastCompletedAt());
        facts.add(new LearningFact("study:summary","STUDY_SUMMARY",Collections.unmodifiableMap(summary)));
        var limitations=new ArrayList<>(ranking.limitations());
        if(attempts.isEmpty()) limitations.add("NO_QUIZ_ATTEMPTS");
        if(study.completedStudySessions()==0) limitations.add("NO_COMPLETED_STUDY_SESSIONS");
        return new NextConceptResponse(courseId,asOf.toString(),ALGORITHM_VERSION,ranking.status(),
            List.copyOf(items),List.copyOf(facts),List.copyOf(limitations));
    }
}
