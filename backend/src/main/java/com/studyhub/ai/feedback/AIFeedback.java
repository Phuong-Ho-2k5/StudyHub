package com.studyhub.ai.feedback;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity @Table(name="ai_feedback")
public class AIFeedback {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long userId;
    @Column(nullable=false,updatable=false) private Long messageId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private FeedbackRating rating;
    @Column(length=2000) private String note;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    protected AIFeedback() {}
    public AIFeedback(Long userId,Long messageId,FeedbackRating rating,String note) {
        this.userId=userId;this.messageId=messageId;this.rating=rating;this.note=note;
        this.createdAt=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);this.updatedAt=createdAt;
    }
    public void replace(FeedbackRating rating,String note) {
        if(this.rating!=rating || !Objects.equals(this.note,note)) {
            this.rating=rating;this.note=note;this.updatedAt=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        }
    }
    public Long getId(){return id;}
    public Long getUserId(){return userId;}
    public Long getMessageId(){return messageId;}
    public FeedbackRating getRating(){return rating;}
    public String getNote(){return note;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
}
