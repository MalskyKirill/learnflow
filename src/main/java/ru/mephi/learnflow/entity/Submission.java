package ru.mephi.learnflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "submissions", uniqueConstraints = @UniqueConstraint(
        name = Submission.UNIQUE_STUDENT_ASSIGNMENT, columnNames = {"student_id", "assignment_id"}))
public class Submission {

    public static final String UNIQUE_STUDENT_ASSIGNMENT = "uk_submissions_student_assignment";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    private Integer score;

    @Column(columnDefinition = "text")
    private String feedback;

    protected Submission() {
    }

    Submission(Assignment assignment, User student, String content) {
        if (student == null || student.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException("Отправить решение может только студент");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Решение не должно быть пустым");
        }
        this.assignment = assignment;
        this.student = student;
        this.content = content;
        this.submittedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public User getStudent() {
        return student;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getContent() {
        return content;
    }

    public Integer getScore() {
        return score;
    }

    public String getFeedback() {
        return feedback;
    }
}
