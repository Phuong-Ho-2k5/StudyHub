package com.studyhub.question;

import java.util.ArrayList;
import java.util.List;

import com.studyhub.quiz.Quiz;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "questions")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "question_text", nullable = false, length = 500)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionType type;

    @Column(name = "reference_answer", length = 4000)
    private String referenceAnswer;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<AnswerOption> options = new ArrayList<>();

    protected Question() {}

    public Question(Quiz quiz, String text, QuestionType type, String referenceAnswer) {
        this.quiz = quiz;
        this.text = text;
        this.type = type;
        this.referenceAnswer = referenceAnswer;
    }

    public Long getId() { return id; }
    public Quiz getQuiz() { return quiz; }
    public String getText() { return text; }
    public QuestionType getType() { return type; }
    public String getReferenceAnswer() { return referenceAnswer; }
    public List<AnswerOption> getOptions() { return options; }

    public void update(String text, QuestionType type, String referenceAnswer, List<AnswerOption> newOptions) {
        this.text = text;
        this.type = type;
        this.referenceAnswer = referenceAnswer;
        options.clear();
        options.addAll(newOptions);
    }

    public void addOption(String text, boolean correct) {
        options.add(new AnswerOption(this, text, correct));
    }
}
