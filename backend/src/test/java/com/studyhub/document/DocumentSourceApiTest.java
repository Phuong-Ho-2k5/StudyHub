package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:source_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
class DocumentSourceApiTest extends SourceApiSupport {
    @MockitoBean DocumentSourceRepository sources;
    @Test void sourceIsScopedToOwnerCourseDocumentAndPublishedChunk() throws Exception {
        String owner=login(),other=login();long course=course(owner),document=document(course,owner);
        String path="/api/courses/"+course+"/documents/"+document+"/chunks/301/source";
        when(sources.findPublished(eq(course),eq(document),eq(301L),anyLong())).thenReturn(Optional.of(
            new DocumentSourceRepository.PublishedSource(course,document,"DB",301L,0,"Atomicity",null,null,false)));
        var success=call("GET",path,owner,null);assertEquals(200,success.statusCode());
        var body=json.readTree(success.body());assertEquals("Atomicity",body.path("content").asText());
        assertEquals(301L,body.path("chunkId").asLong());assertTrue(body.path("pageStart").isNull());
        assertTrue(body.path("fileUrl").isNull());assertFalse(body.has("storagePath"));
        assertEquals(401,call("GET",path,null,null).statusCode());
        assertEquals(404,call("GET",path,other,null).statusCode());
        long another=course(owner);
        assertEquals(404,call("GET",path.replace("/courses/"+course+"/","/courses/"+another+"/"),owner,null).statusCode());
        when(sources.findPublished(eq(course),eq(document),eq(301L),anyLong())).thenReturn(Optional.empty());
        assertEquals(409,call("GET",path,owner,null).statusCode());
        assertEquals(409,call("GET",path.replace("301/source","999/source"),owner,null).statusCode());
    }
}
