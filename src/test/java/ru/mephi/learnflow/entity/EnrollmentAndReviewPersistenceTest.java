package ru.mephi.learnflow.entity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnrollmentAndReviewPersistenceTest {

    @Autowired
    private EntityManager entities;

    @Autowired
    private JdbcTemplate jdbc;

    private final User teacher = new User("Ирина", "irina@example.org", UserRole.TEACHER);
    private final User student = new User("Анна", "anna@example.org", UserRole.STUDENT);
    private final Category category = new Category("Базы данных");
    private final Course course = new Course("Hibernate", null, category, teacher);

    @BeforeEach
    void saveCourseAndUsers() {
        entities.persist(teacher);
        entities.persist(student);
        entities.persist(category);
        entities.persist(course);
    }

    @Test
    void allowsDifferentStudentCoursePairsAndRejectsADuplicatePair() {
        User peer = new User("Иван", "ivan@example.org", UserRole.STUDENT);
        Course secondCourse = new Course("SQL", null, category, teacher);
        entities.persist(peer);
        entities.persist(secondCourse);
        Enrollment first = new Enrollment(student, course);
        entities.persist(first);
        entities.persist(new Enrollment(peer, course));
        entities.persist(new Enrollment(student, secondCourse));
        entities.flush();
        entities.clear();

        Enrollment loaded = entities.find(Enrollment.class, first.getId());
        assertThat(loaded.getStudent().getId()).isEqualTo(student.getId());
        assertThat(loaded.getCourse().getId()).isEqualTo(course.getId());
        assertThat(loaded.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(loaded.getEnrolledAt()).isNotNull();
        assertThatThrownBy(() -> jdbc.update("""
                insert into enrollments (student_id, course_id, enrolled_at, status)
                values (?, ?, current_timestamp, 'ACTIVE')
                """, student.getId(), course.getId()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void loadsReviewFromCourseAndStudent() {
        CourseReview review = course.addReview(student, 5, "Понятные примеры");
        entities.persist(review);
        entities.flush();
        entities.clear();

        CourseReview loaded = entities.find(Course.class, course.getId()).getReviews().getFirst();
        assertThat(loaded.getId()).isEqualTo(review.getId());
        assertThat(loaded.getRating()).isEqualTo(5);
        assertThat(loaded.getComment()).isEqualTo("Понятные примеры");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(entities.find(User.class, student.getId()).getReviews()).containsExactly(loaded);
    }

    @Test
    void rejectsTeacherEnrollmentAndInvalidReviews() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Enrollment(teacher, course));
        assertThatIllegalArgumentException().isThrownBy(() -> course.addReview(teacher, 5, null));
        assertThatIllegalArgumentException().isThrownBy(() -> course.addReview(student, 0, null));
        assertThatIllegalArgumentException().isThrownBy(() -> course.addReview(student, 6, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void databaseRejectsReviewRatingOutsideAllowedRange(int invalidRating) {
        CourseReview review = course.addReview(student, 5, "Понятные примеры");
        entities.persist(review);
        entities.flush();

        assertThatThrownBy(() -> jdbc.update("update course_reviews set rating = ? where id = ?",
                invalidRating, review.getId()))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause()
                .isInstanceOfSatisfying(SQLException.class, cause -> assertThat(cause.getSQLState()).isEqualTo("23514"));
    }
}
