package ru.mephi.learnflow.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "student_answers", uniqueConstraints = @UniqueConstraint(
        name = "uk_student_answers_submission_question", columnNames = {"submission_id", "question_id"}))
public class StudentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private QuizSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private AnswerOption option;

    protected StudentAnswer() {
    }

    StudentAnswer(QuizSubmission submission, AnswerOption option) {
        this.submission = submission;
        this.question = option.getQuestion();
        this.option = option;
    }

    public Long getId() {
        return id;
    }

    public QuizSubmission getSubmission() {
        return submission;
    }

    public Question getQuestion() {
        return question;
    }

    public AnswerOption getOption() {
        return option;
    }
}
