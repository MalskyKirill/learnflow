package ru.mephi.learnflow.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import ru.mephi.learnflow.exception.InvalidInputException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "enrollments", uniqueConstraints = @UniqueConstraint(
        name = Enrollment.UNIQUE_STUDENT_COURSE, columnNames = {"student_id", "course_id"}))
public class Enrollment {

    public static final String UNIQUE_STUDENT_COURSE = "uk_enrollments_student_course";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status;

    protected Enrollment() {
    }

    public Enrollment(User student, Course course) {
        if (student == null || student.getRole() != UserRole.STUDENT) {
            throw new InvalidInputException("Записаться на курс может только студент");
        }
        this.student = student;
        this.course = course;
        this.enrolledAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.status = EnrollmentStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public User getStudent() {
        return student;
    }

    public Course getCourse() {
        return course;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }
}
