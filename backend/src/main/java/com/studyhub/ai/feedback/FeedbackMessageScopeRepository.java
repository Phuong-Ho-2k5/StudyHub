package com.studyhub.ai.feedback;

import com.studyhub.ai.ChatMessageRole;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackMessageScopeRepository {
    private final JdbcTemplate jdbc;
    private final String userLock;
    public FeedbackMessageScopeRepository(JdbcTemplate jdbc){
        this.jdbc=jdbc;
        boolean postgres=Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)
            connection->connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")));
        userLock=postgres?" for key share":" for update";
    }
    public boolean lockUser(long userId) {
        // Pin the FK parent before Course/Session locks. KEY SHARE allows concurrent writes
        // and FK checks, but keeps a user cascade from deadlocking against our insert.
        return !jdbc.queryForList("select id from users where id=?"+userLock,Long.class,userId).isEmpty();
    }
    public record MessageScope(long sessionId,long courseId,ChatMessageRole role) {}
    private static final String SCOPE="""
        select s.id,s.course_id,m.role from chat_messages m
        join chat_sessions s on s.id=m.session_id join courses c on c.id=s.course_id
        join workspaces w on w.id=c.workspace_id
        where m.id=? and s.user_id=? and w.owner_id=?
        """;
    public Optional<MessageScope> findOwnedScope(long messageId,long userId) {
        return jdbc.query(SCOPE,(rs,n)->new MessageScope(rs.getLong(1),rs.getLong(2),ChatMessageRole.valueOf(rs.getString(3))),messageId,userId,userId).stream().findFirst();
    }
    public Optional<MessageScope> findOwnedScopeInSession(long messageId,long sessionId,long courseId,long userId) {
        // Lock the message too: direct SQL deletion cannot remove it between the recheck and insert.
        return jdbc.query(SCOPE+" and s.id=? and s.course_id=? for update of m",
            (rs,n)->new MessageScope(rs.getLong(1),rs.getLong(2),ChatMessageRole.valueOf(rs.getString(3))),messageId,userId,userId,sessionId,courseId).stream().findFirst();
    }
}
