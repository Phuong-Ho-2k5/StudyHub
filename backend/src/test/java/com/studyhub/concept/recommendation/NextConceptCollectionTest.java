package com.studyhub.concept.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:recommendations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "studyhub.ai.token=", "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="RUN_RECOMMENDATION_COLLECTION",matches="1")
class NextConceptCollectionTest extends RecommendationTestSupport {
    @Test void runsSavedAssertionsAgainstRealSpringHttp() throws Exception {
        var owner=owner();var empty=owner();var mastered=owner();var cycle=owner();var foreign=owner();
        long a=concept(owner,"A",20),b=concept(owner,"B",40),c=concept(owner,"C",10);
        edge(b,a);edge(c,b);concept(mastered,"Mastered",80);
        long x=concept(cycle,"X",10),y=concept(cycle,"Y",20);edge(x,y);edge(y,x);
        var vars=new LinkedHashMap<String,String>();
        vars.put("baseUrl","http://localhost:"+port);vars.put("accessToken",owner.token());
        vars.put("emptyAccessToken",empty.token());vars.put("masteredAccessToken",mastered.token());vars.put("cycleAccessToken",cycle.token());
        vars.put("courseId",""+owner.courseId());vars.put("emptyCourseId",""+empty.courseId());
        vars.put("masteredCourseId",""+mastered.courseId());vars.put("cycleCourseId",""+cycle.courseId());
        vars.put("foreignCourseId",""+foreign.courseId());vars.put("foundationId",""+a);
        Path root=Path.of("..").toAbsolutePath().normalize();
        Path environment=Files.createTempFile("recommendations-",".environment.yaml");
        Path log=root.resolve("logs/next-concepts/collection.log");Files.createDirectories(log.getParent());
        try {
            json.writeValue(environment.toFile(),Map.of("name","Disposable recommendation fixtures","values",vars.entrySet().stream()
                .map(e->Map.of("key",e.getKey(),"value",e.getValue(),"enabled",true,
                    "type",e.getKey().toLowerCase().contains("token")?"secret":"default")).toList()));
            var process=new ProcessBuilder("postman","collection","run",root.resolve("infra/testing/recommendations/collection").toString(),
                "--environment",environment.toString(),"--no-report-events").directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if(!process.waitFor(90,TimeUnit.SECONDS)) {process.destroyForcibly();fail("Collection timed out; see local collection log");}
            assertEquals(0,process.exitValue(),"See logs/next-concepts/collection.log");
        } finally {Files.deleteIfExists(environment);}
    }
    private void edge(long dependent,long prerequisite) {jdbc.update("insert into concept_dependencies(concept_id,prerequisite_id) values (?,?)",dependent,prerequisite);}
}
