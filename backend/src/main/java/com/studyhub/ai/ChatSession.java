package com.studyhub.ai;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="chat_sessions")
public class ChatSession {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false, updatable=false) private Long userId;
    @Column(nullable=false, updatable=false) private Long courseId;
    @Column(nullable=false, length=150) private String title;
    @Column(nullable=false, updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;

    protected ChatSession() {}
    public ChatSession(Long userId, Long courseId, String title) {
        this.userId=userId; this.courseId=courseId; this.title=title;
        this.createdAt=Instant.now(); this.updatedAt=createdAt;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getCourseId() { return courseId; }
    public String getTitle() { return title; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { updatedAt=Instant.now(); }
}
