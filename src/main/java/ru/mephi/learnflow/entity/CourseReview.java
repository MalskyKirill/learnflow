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
import org.hibernate.annotations.Check;

import java.time.Instant;

@Entity
@Table(name = "course_reviews")
@Check(constraints = "rating between 1 and 5")
public class CourseReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CourseReview() {
    }

    CourseReview(Course course, User student, int rating, String comment) {
        if (student == null || student.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException("Оставить отзыв может только студент");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Оценка должна быть от 1 до 5");
        }
        this.course = course;
        this.student = student;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public User getStudent() {
        return student;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
