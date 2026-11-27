package ru.mephi.learnflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;
import ru.mephi.learnflow.exception.InvalidInputException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "assignments")
@Check(constraints = "max_score > 0")
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "max_score", nullable = false)
    private int maxScore;

    @OneToMany(mappedBy = "assignment", fetch = FetchType.LAZY)
    private List<Submission> submissions = new ArrayList<>();

    protected Assignment() {
    }

    Assignment(Lesson lesson, String title, String description, Instant dueAt, int maxScore) {
        if (title == null || title.isBlank() || title.length() > 255) {
            throw new InvalidInputException("Название задания должно содержать от 1 до 255 символов");
        }
        if (maxScore < 1) {
            throw new InvalidInputException("Максимальный балл должен быть положительным");
        }
        this.lesson = lesson;
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.maxScore = maxScore;
    }

    public Long getId() {
        return id;
    }

    public Lesson getLesson() {
        return lesson;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public List<Submission> getSubmissions() {
        return Collections.unmodifiableList(submissions);
    }

    public Submission addSubmission(User student, String content) {
        Submission submission = new Submission(this, student, content);
        submissions.add(submission);
        return submission;
    }
}
