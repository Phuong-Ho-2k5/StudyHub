package com.studyhub.ai.advice;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

abstract class AdviceApiSupport {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();
    final HttpClient http = HttpClient.newHttpClient();
    record Owner(String token, long userId, long courseId) {}

    Owner owner() throws Exception {
        String email = "advice-" + UUID.randomUUID() + "@example.com";
        var credentials = Map.of("email", email, "password", "password123");
        var registration = new HashMap<String,Object>(credentials); registration.put("name", "Advice Tester");
        assertEquals(201, call("POST", "/api/auth/register", null, registration).statusCode());
        String token = body(call("POST", "/api/auth/login", null, credentials)).path("accessToken").asText();
        var response = call("POST", "/api/workspaces", token, Map.of("name", "Learning", "description", "Advice test"));
        assertEquals(201, response.statusCode(), response.body());
        long workspace = body(response).path("id").asLong();
        response = call("POST", "/api/workspaces/" + workspace + "/courses", token, Map.of("name", "Databases", "description", "Advice test"));
        assertEquals(201, response.statusCode(), response.body());
        long course = body(response).path("id").asLong();
        assertTrue(course > 0);
        return new Owner(token, jdbc.queryForObject("select id from users where email=?", Long.class, email), course);
    }

    long concept(Owner owner, String name, int confidence) {
        jdbc.update("insert into concepts(course_id,name,confidence,status) values (?,?,?,'NEW')", owner.courseId(), name, confidence);
        return jdbc.queryForObject("select id from concepts where course_id=? and name=?", Long.class, owner.courseId(), name);
    }

    long attempt(Owner courseOwner, long userId, double score) {
        String title = "Quiz " + UUID.randomUUID();
        jdbc.update("insert into quizzes(course_id,title,status,source_type) values (?,?,'PUBLISHED','MANUAL')", courseOwner.courseId(), title);
        long quiz = jdbc.queryForObject("select id from quizzes where title=?", Long.class, title);
        jdbc.update("insert into quiz_attempts(quiz_id,user_id,total_questions,graded_count,correct_count,incorrect_count,pending_count,question_percentage) values (?,?,10,10,2,8,0,?)", quiz, userId, score);
        return jdbc.queryForObject("select id from quiz_attempts where quiz_id=?", Long.class, quiz);
    }

    JsonNode body(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    HttpResponse<String> call(String method, String path, String token, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
}
