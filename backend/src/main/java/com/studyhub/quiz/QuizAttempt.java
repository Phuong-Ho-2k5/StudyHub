package com.studyhub.quiz;

import java.sql.Timestamp;

import org.hibernate.annotations.CreationTimestamp;

import com.studyhub.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer totalQuestions;

    @Column(nullable = false)
    private Integer gradedCount;

    @Column(nullable = false)
    private Integer correctCount;

    @Column(nullable = false)
    private Integer incorrectCount;

    @Column(nullable = false)
    private Integer pendingCount;

    @Column(nullable = false)
    private Double questionPercentage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Timestamp createdAt;

    protected QuizAttempt() {}

    public QuizAttempt(Quiz quiz, User user, int totalQuestions, int gradedCount,
            int correctCount, int incorrectCount, int pendingCount, double questionPercentage) {
        this.quiz = quiz;
        this.user = user;
        this.totalQuestions = totalQuestions;
        this.gradedCount = gradedCount;
        this.correctCount = correctCount;
        this.incorrectCount = incorrectCount;
        this.pendingCount = pendingCount;
        this.questionPercentage = questionPercentage;
    }

    public Long getId() { 
        return id; 
    }

    public Quiz getQuiz() { 
        return quiz; 
    }

    public User getUser() { 
        return user; 
    }

    public Integer getTotalQuestions() { 
        return totalQuestions; 
    }

    public Integer getGradedCount() { 
        return gradedCount; 
    }

    public Integer getCorrectCount() { 
        return correctCount; 
    }

    public Integer getIncorrectCount() { 
        return incorrectCount; 
    
    }

    public Integer getPendingCount() { 
        return pendingCount; 
    }

    public Double getQuestionPercentage() { 
        return questionPercentage; 
    }

    public Timestamp getCreatedAt() { 
        return createdAt; 
    }
}
