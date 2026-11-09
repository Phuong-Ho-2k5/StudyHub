package com.studyhub.ai.job;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:ai_jobs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
abstract class AIJobTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    record Fixture(long user,long workspace,long course,long document) {}
    Fixture fixture() {
        String email=UUID.randomUUID()+"@test.local";
        jdbc.update("insert into users(email,password_hash,name) values (?,'hash','Tester')",email);
        long user=jdbc.queryForObject("select id from users where email=?",Long.class,email);
        jdbc.update("insert into workspaces(name,owner_id) values ('Jobs',?)",user);
        long workspace=jdbc.queryForObject("select id from workspaces where owner_id=?",Long.class,user);
        jdbc.update("insert into courses(name,workspace_id) values ('Course',?)",workspace);
        long course=jdbc.queryForObject("select id from courses where workspace_id=?",Long.class,workspace);
        jdbc.update("insert into documents(title,file_name,course_id) values ('Doc','doc.pdf',?)",course);
        long document=jdbc.queryForObject("select id from documents where course_id=?",Long.class,course);
        return new Fixture(user,workspace,course,document);
    }
    UUID pending(Fixture f) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into ai_jobs(id,requested_by,course_id,document_id,type,status,created_at,updated_at) values (?,?,?,?,'INGEST_DOCUMENT','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id.toString(),f.user(),f.course(),f.document());
        return id;
    }
    TransactionTemplate tx() {return new TransactionTemplate(manager);}
}
