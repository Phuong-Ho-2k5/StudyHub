package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:file_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
class DocumentCitationFileApiTest extends SourceApiSupport {
    static final Path ROOT;
    static { try { ROOT=Files.createTempDirectory("studyhub-file-test-"); } catch(Exception e) { throw new RuntimeException(e); } }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry r) { r.add("studyhub.storage.root",()->ROOT.toString()); }
    @Autowired JdbcTemplate jdbc;
    @Test void fileRequiresOwnerAndManagedStorageAndReturnsPrivatePdf() throws Exception {
        String owner=login(),other=login();long document=document(course(owner),owner);
        String path="/api/documents/"+document+"/file";
        assertEquals(401,call("GET",path,null,null).statusCode());assertEquals(404,call("GET",path,other,null).statusCode());
        assertEquals(409,call("GET",path,owner,null).statusCode());
        String key=UUID.randomUUID()+".pdf";
        byte[] bytes;
        try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var output=new java.io.ByteArrayOutputStream()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());pdf.save(output);bytes=output.toByteArray();
        }
        Files.write(ROOT.resolve(key),bytes);
        jdbc.update("UPDATE documents SET managed_file=true,storage_path=?,file_name=? WHERE id=?",key,"Tiếng Việt\r\nInjected.pdf",document);
        var response=call("GET",path,owner,null);assertEquals(200,response.statusCode());assertArrayEquals(bytes,response.body());
        assertEquals("application/pdf",response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("nosniff",response.headers().firstValue("X-Content-Type-Options").orElseThrow());
        String disposition=response.headers().firstValue("Content-Disposition").orElseThrow();
        assertTrue(disposition.startsWith("inline;"));assertFalse(disposition.contains("\r"));assertFalse(disposition.contains("\n"));
        assertTrue(response.headers().firstValue("Injected").isEmpty());
        Files.delete(ROOT.resolve(key));assertEquals(409,call("GET",path,owner,null).statusCode());
        jdbc.update("UPDATE documents SET storage_path=? WHERE id=?","../outside.pdf",document);
        var invalid=call("GET",path,owner,null);assertEquals(409,invalid.statusCode());
        assertFalse(new String(invalid.body()).contains("outside.pdf"));
    }
    @Test void managedKeySymlinkCannotExposeExternalFile() throws Exception {
        Path external=Files.createTempFile("studyhub-outside-",".pdf");String key=UUID.randomUUID()+".pdf";
        try {
            Files.writeString(external,"private external content");
            try { Files.createSymbolicLink(ROOT.resolve(key),external); }
            catch(java.io.IOException|UnsupportedOperationException denied) { org.junit.jupiter.api.Assumptions.abort("Symlink creation unavailable"); }
            String owner=login();long document=document(course(owner),owner);
            jdbc.update("UPDATE documents SET managed_file=true,storage_path=? WHERE id=?",key,document);
            assertEquals(409,call("GET","/api/documents/"+document+"/file",owner,null).statusCode());
        } finally { Files.deleteIfExists(ROOT.resolve(key));Files.deleteIfExists(external); }
    }
    @org.junit.jupiter.api.AfterAll static void cleanup() throws Exception { Files.deleteIfExists(ROOT); }
}
