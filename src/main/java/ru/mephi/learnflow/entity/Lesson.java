package ru.mephi.learnflow.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.UniqueConstraint;
import ru.mephi.learnflow.exception.InvalidInputException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "lessons", uniqueConstraints = @UniqueConstraint(columnNames = {"module_id", "position"}))
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private int position;

    @OneToMany(mappedBy = "lesson", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Assignment> assignments = new ArrayList<>();

    protected Lesson() {
    }

    Lesson(CourseModule module, String title, String content, int position) {
        updateDetails(title, content);
        this.module = module;
        this.position = position;
    }

    public void updateDetails(String title, String content) {
        if (title == null || title.isBlank() || title.length() > 255) {
            throw new InvalidInputException("Название урока должно содержать от 1 до 255 символов");
        }
        this.title = title;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public CourseModule getModule() {
        return module;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public int getPosition() {
        return position;
    }

    public List<Assignment> getAssignments() {
        return Collections.unmodifiableList(assignments);
    }

    public Assignment addAssignment(String title, String description, Instant dueAt, int maxScore) {
        Assignment assignment = new Assignment(this, title, description, dueAt, maxScore);
        assignments.add(assignment);
        return assignment;
    }

    public void removeAssignment(Assignment assignment) {
        assignments.remove(assignment);
    }
}
