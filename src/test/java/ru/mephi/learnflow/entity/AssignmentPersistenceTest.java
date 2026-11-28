package ru.mephi.learnflow.entity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssignmentPersistenceTest {

    @Autowired
    private EntityManager entities;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savesAssignmentsAndAcceptsOnlyOneSubmissionPerStudentAndAssignment() {
        Category category = new Category("Базы данных");
        User teacher = new User("Мария", "maria@example.org", UserRole.TEACHER);
        User anna = new User("Анна", "anna@example.org", UserRole.STUDENT);
        User ivan = new User("Иван", "ivan@example.org", UserRole.STUDENT);
        entities.persist(category);
        entities.persist(teacher);
        entities.persist(anna);
        entities.persist(ivan);

        Course course = new Course("Hibernate", null, category, teacher);
        Lesson lesson = course.addModule("JPA").addLesson("Связи", null);
        Instant dueAt = Instant.parse("2027-01-01T12:00:00Z");
        Assignment mapping = lesson.addAssignment("Маппинг", "Опишите связи сущностей", dueAt, 20);
        Assignment queries = lesson.addAssignment("Запросы", null, null, 10);
        entities.persist(course);

        Submission first = mapping.addSubmission(anna, "Связь многие к одному");
        entities.persist(first);
        entities.persist(mapping.addSubmission(ivan, "Связь один ко многим"));
        entities.persist(queries.addSubmission(anna, "select c from Course c"));
        entities.flush();
        entities.clear();

        Lesson loadedLesson = entities.find(Lesson.class, lesson.getId());
        assertThat(loadedLesson.getAssignments()).extracting(Assignment::getTitle)
                .containsExactlyInAnyOrder("Маппинг", "Запросы");
        Assignment loadedAssignment = entities.find(Assignment.class, mapping.getId());
        assertThat(loadedAssignment.getDueAt()).isEqualTo(dueAt);
        assertThat(loadedAssignment.getMaxScore()).isEqualTo(20);
        assertThat(loadedAssignment.getSubmissions()).extracting(Submission::getContent)
                .containsExactlyInAnyOrder("Связь многие к одному", "Связь один ко многим");

        Submission loadedSubmission = entities.find(Submission.class, first.getId());
        assertThat(loadedSubmission.getStudent().getId()).isEqualTo(anna.getId());
        assertThat(loadedSubmission.getSubmittedAt()).isNotNull();
        assertThat(loadedSubmission.getScore()).isNull();
        assertThat(loadedSubmission.getFeedback()).isNull();

        assertThatThrownBy(() -> jdbc.update("""
                insert into submissions (student_id, assignment_id, submitted_at, content)
                values (?, ?, current_timestamp, ?)
                """, anna.getId(), mapping.getId(), "Повторная сдача"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void databaseRejectsNonPositiveAssignmentMaximum() {
        Category category = new Category("Базы данных");
        User teacher = new User("Мария", "maria@example.org", UserRole.TEACHER);
        entities.persist(category);
        entities.persist(teacher);
        Course course = new Course("Hibernate", null, category, teacher);
        Assignment assignment = course.addModule("JPA").addLesson("Связи", null)
                .addAssignment("Маппинг", null, null, 20);
        entities.persist(course);
        entities.flush();

        assertThatThrownBy(() -> jdbc.update("update assignments set max_score = 0 where id = ?", assignment.getId()))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause()
                .isInstanceOfSatisfying(SQLException.class, cause -> assertThat(cause.getSQLState()).isEqualTo("23514"));
    }
}
