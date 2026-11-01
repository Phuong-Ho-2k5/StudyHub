package com.studyhub.ai;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="chat_messages")
public class ChatMessage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, updatable=false) private Long sessionId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private ChatMessageRole role;
    @Column(nullable=false, columnDefinition="text") private String content;
    @Column(length=30) private String status;
    @Column(length=255) private String model;
    @Column(length=100) private String promptVersion;
    @Column(nullable=false, columnDefinition="text") private String citationsJson;
    @Column(nullable=false, updatable=false) private Instant createdAt;

    protected ChatMessage() {}
    public ChatMessage(Long sessionId, ChatMessageRole role, String content, String status,
            String model, String promptVersion, String citationsJson) {
        this.sessionId=sessionId; this.role=role; this.content=content; this.status=status;
        this.model=model; this.promptVersion=promptVersion; this.citationsJson=citationsJson;
        this.createdAt=Instant.now();
    }
    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public ChatMessageRole getRole() { return role; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public String getModel() { return model; }
    public String getPromptVersion() { return promptVersion; }
    public String getCitationsJson() { return citationsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
