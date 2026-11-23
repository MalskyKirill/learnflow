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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<CourseModule> modules = new ArrayList<>();

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    private List<CourseReview> reviews = new ArrayList<>();

    protected Course() {
    }

    public Course(String title, String description, Category category, User teacher) {
        if (teacher == null || teacher.getRole() != UserRole.TEACHER) {
            throw new IllegalArgumentException("Вести курс может только преподаватель");
        }
        this.title = title;
        this.description = description;
        this.category = category;
        this.teacher = teacher;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public User getTeacher() {
        return teacher;
    }

    public List<CourseModule> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<CourseReview> getReviews() {
        return Collections.unmodifiableList(reviews);
    }

    public CourseReview addReview(User student, int rating, String comment) {
        CourseReview review = new CourseReview(this, student, rating, comment);
        reviews.add(review);
        student.addReview(review);
        return review;
    }

    public CourseModule addModule(String title) {
        int position = modules.isEmpty() ? 1 : modules.getLast().getPosition() + 1;
        CourseModule module = new CourseModule(this, title, position);
        modules.add(module);
        return module;
    }
}
