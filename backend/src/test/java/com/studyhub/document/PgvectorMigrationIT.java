package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Runs only with -Ppgvector-it against a disposable PostgreSQL database. */
class PgvectorMigrationIT {
    private static Connection connection;
    private static String schema;
    private static Flyway flyway;
    private long documentId;

    @BeforeAll
    static void migrate() throws Exception {
        String url = System.getenv("PGVECTOR_TEST_URL");
        assertNotNull(url, "Set PGVECTOR_TEST_URL to a disposable PostgreSQL database");
        String user = System.getenv("PGVECTOR_TEST_USER");
        String password = System.getenv("PGVECTOR_TEST_PASSWORD");
        schema = "pgvector_test_" + UUID.randomUUID().toString().replace("-", "");
        connection = DriverManager.getConnection(url, user, password);
        flyway = Flyway.configure().dataSource(url, user, password).defaultSchema(schema)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql").load();
        // Exercise an upgrade from the current application schema, not just SQL in isolation.
        Flyway.configure().configuration(flyway.getConfiguration()).target("17").load().migrate();
        connection.createStatement().execute("SET search_path TO " + schema + ", public");
        connection.createStatement().executeUpdate("INSERT INTO users(email,password_hash,name) "
                + "VALUES ('before-migration@example.com','test','Existing user')");
        flyway.migrate();
        assertEquals(0, flyway.migrate().migrationsExecuted, "Migration must be repeatable without schema changes");
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT name FROM users WHERE email='before-migration@example.com'")) {
            assertTrue(result.next());
            assertEquals("Existing user", result.getString(1));
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (connection != null) {
            try (var closingConnection = connection; var statement = closingConnection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @BeforeEach
    void seedDocument() throws Exception {
        connection.setAutoCommit(false);
        long userId = id("INSERT INTO users(email,password_hash,name) VALUES ('" + UUID.randomUUID()
                + "@example.com','test','Learner') RETURNING id");
        long workspaceId = id("INSERT INTO workspaces(name,owner_id) VALUES ('Vector test'," + userId + ") RETURNING id");
        long courseId = id("INSERT INTO courses(name,workspace_id) VALUES ('Course'," + workspaceId + ") RETURNING id");
        documentId = id("INSERT INTO documents(course_id,title,file_name) VALUES (" + courseId
                + ",'PDF','test.pdf') RETURNING id");
    }

    @AfterEach
    void rollback() throws Exception {
        connection.rollback();
        connection.setAutoCommit(true);
    }

    @Test
    void storesVectorsAndFindsNearestChunk() throws Exception {
        insert(0, "[1,0,0]", 3);
        insert(1, "[0,1,0]", 3);
        try (var result = connection.createStatement().executeQuery("SELECT chunk_index, embedding_dimensions, "
                + "embedding_model, embedding <=> '[1,0,0]'::vector AS distance FROM document_chunks "
                + "WHERE document_id=" + documentId + " ORDER BY distance LIMIT 1")) {
            assertTrue(result.next());
            assertEquals(0, result.getInt("chunk_index"));
            assertEquals(3, result.getInt("embedding_dimensions"));
            assertEquals("test-model", result.getString("embedding_model"));
            assertEquals(0.0, result.getDouble("distance"), 1e-6);
        }
    }

    @Test
    void rejectsDimensionMismatch() {
        assertSqlState("23514", () -> insert(0, "[1,0,0]", 2));
    }

    @Test
    void rejectsDuplicateChunkInDocument() throws Exception {
        insert(0, "[1,0,0]", 3);
        assertSqlState("23505", () -> insert(0, "[0,1,0]", 3));
    }

    @Test
    void rejectsMissingDocument() {
        documentId = -1;
        assertSqlState("23503", () -> insert(0, "[1,0,0]", 3));
    }

    @Test
    void deletesChunksWithDocument() throws Exception {
        insert(0, "[1,0,0]", 3);
        connection.createStatement().executeUpdate("DELETE FROM documents WHERE id=" + documentId);
        assertEquals(0, id("SELECT count(*) FROM document_chunks WHERE document_id=" + documentId));
    }

    @Test
    void rejectsNegativeChunkIndex() {
        assertSqlState("23514", () -> insert(-1, "[1,0,0]", 3));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "content = ''", "embedding_model = ''", "embedding_dimensions = 0",
            "page_start = 0, page_end = 1", "page_start = 3, page_end = 2",
            "page_start = 1, page_end = NULL", "page_start = NULL, page_end = 2"
    })
    void rejectsInvalidMetadata(String assignment) throws Exception {
        insert(0, "[1,0,0]", 3);
        assertSqlState("23514", () -> connection.createStatement()
                .executeUpdate("UPDATE document_chunks SET " + assignment + " WHERE document_id=" + documentId));
    }

    private void insert(int index, String vector, int dimensions) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO document_chunks "
                + "(document_id,chunk_index,content,embedding,embedding_model,embedding_dimensions) "
                + "VALUES (?,?,?,?::vector,?,?)")) {
            statement.setLong(1, documentId);
            statement.setInt(2, index);
            statement.setString(3, "Nội dung học tập");
            statement.setString(4, vector);
            statement.setString(5, "test-model");
            statement.setInt(6, dimensions);
            statement.executeUpdate();
        }
    }

    private long id(String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void assertSqlState(String state, org.junit.jupiter.api.function.Executable action) {
        assertEquals(state, assertThrows(SQLException.class, action).getSQLState());
    }
}
