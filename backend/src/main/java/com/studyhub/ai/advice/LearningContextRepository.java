package com.studyhub.ai.advice;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LearningContextRepository {
    private final JdbcTemplate jdbc;
    public LearningContextRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record ConceptRow(long id, String name, int confidence) {}
    public record PrerequisiteRow(long dependentId, String dependentName, int dependentConfidence,
                                  long prerequisiteId, String prerequisiteName, int prerequisiteConfidence) {}
    public record AttemptRow(long id, long quizId, double questionPercentage, int correctCount,
                             int totalQuestions, String createdAt) {}
    public record StudySummary(long completedStudySessions, long totalStudySeconds, String lastCompletedAt) {}

    public List<ConceptRow> concepts(long courseId, long userId) {
        return jdbc.query("""
            select c.id,c.name,c.confidence from concepts c
            join courses co on co.id=c.course_id join workspaces w on w.id=co.workspace_id
            where c.course_id=? and w.owner_id=? order by c.confidence,c.id limit 11
            """, (r,n) -> new ConceptRow(r.getLong(1),r.getString(2),r.getInt(3)), courseId,userId);
    }

    public List<PrerequisiteRow> prerequisites(long courseId, long userId, List<Long> dependentIds) {
        if (dependentIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(dependentIds.size(), "?"));
        var parameters = new java.util.ArrayList<Object>(); parameters.add(courseId); parameters.add(userId); parameters.addAll(dependentIds);
        return jdbc.query("""
            select c.id,c.name,c.confidence,p.id,p.name,p.confidence from concept_dependencies d
            join concepts c on c.id=d.concept_id join concepts p on p.id=d.prerequisite_id
            join courses co on co.id=c.course_id join workspaces w on w.id=co.workspace_id
            where c.course_id=? and w.owner_id=? and p.course_id=c.course_id and c.id in (
            """ + placeholders + ") order by c.id,p.id limit 21",
            (r,n) -> new PrerequisiteRow(r.getLong(1),r.getString(2),r.getInt(3),r.getLong(4),r.getString(5),r.getInt(6)), parameters.toArray());
    }

    public List<AttemptRow> attempts(long courseId, long userId) {
        return jdbc.query("""
            select a.id,a.quiz_id,a.question_percentage,a.correct_count,a.total_questions,a.created_at
            from quiz_attempts a join quizzes q on q.id=a.quiz_id
            join courses co on co.id=q.course_id join workspaces w on w.id=co.workspace_id
            where q.course_id=? and a.user_id=? and w.owner_id=? order by a.created_at desc,a.id desc limit 5
            """, (r,n) -> new AttemptRow(r.getLong(1),r.getLong(2),r.getDouble(3),r.getInt(4),r.getInt(5), timestamp(r,6)), courseId,userId,userId);
    }

    public StudySummary study(long courseId, long userId) {
        return jdbc.queryForObject("""
            select count(s.id),coalesce(sum(s.duration_seconds),0),max(s.end_time)
            from study_sessions s join courses co on co.id=s.course_id join workspaces w on w.id=co.workspace_id
            where s.course_id=? and s.user_id=? and w.owner_id=? and s.end_time is not null
            """, (r,n) -> new StudySummary(r.getLong(1),r.getLong(2), timestamp(r,3)), courseId,userId,userId);
    }

    private static String timestamp(ResultSet row, int column) throws SQLException {
        // Match the existing JPA Timestamp mapping for timezone-less Attempt.createdAt.
        // TIMESTAMPTZ study end times retain their instant with either JDBC conversion.
        var value = row.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }
}
