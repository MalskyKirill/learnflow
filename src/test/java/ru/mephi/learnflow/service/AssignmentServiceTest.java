package ru.mephi.learnflow.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.dto.AssignmentDetails;
import ru.mephi.learnflow.dto.CourseDetails;
import ru.mephi.learnflow.dto.CourseModuleDetails;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.exception.CourseTeacherRequiredException;
import ru.mephi.learnflow.repository.AssignmentRepository;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.UserRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssignmentServiceTest {

    @Autowired
    private AssignmentService service;

    @Autowired
    private AssignmentRepository assignments;

    @Autowired
    private CourseService courses;

    @Autowired
    private CourseContentService content;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entities;

    private Long teacherId;
    private Long lessonId;

    @BeforeEach
    void createCourseAndLesson() {
        teacherId = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER)).getId();
        Category category = categories.save(new Category("Базы данных"));
        CourseDetails course = courses.createCourse("ORM", null, category.getId(), teacherId);
        CourseModuleDetails module = content.addModule(course.id(), teacherId, "Основы");
        lessonId = content.addLesson(module.id(), teacherId, "Связи", "Внешние ключи").id();
        entities.clear();
    }

    @Test
    void teacherCreatesAssignmentForOwnCourse() {
        Instant deadline = Instant.parse("2027-01-01T12:00:00Z");
        AssignmentDetails created = service.createAssignment(
                lessonId, teacherId, "JPA-связи", "Опишите связи сущностей", deadline, 20);
        entities.flush();
        entities.clear();

        assertThat(created.id()).isNotNull();
        assertThat(assignments.findAllByLesson_IdOrderByIdAsc(lessonId)).singleElement()
                .extracting(AssignmentDetails::from)
                .isEqualTo(new AssignmentDetails(
                        created.id(), lessonId, "JPA-связи", "Опишите связи сущностей", deadline, 20));
    }

    @Test
    void rejectsAnotherTeacherAndStudent() {
        Long otherTeacherId = users.save(new User("Ирина", "irina@example.org", UserRole.TEACHER)).getId();
        Long studentId = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT)).getId();

        assertThatThrownBy(() -> service.createAssignment(lessonId, otherTeacherId, "Задание", null, null, 20))
                .isInstanceOf(CourseTeacherRequiredException.class);
        assertThatThrownBy(() -> service.createAssignment(lessonId, studentId, "Задание", null, null, 20))
                .isInstanceOf(CourseTeacherRequiredException.class);
        assertThat(assignments.findAllByLesson_IdOrderByIdAsc(lessonId)).isEmpty();
    }

    @Test
    void rejectsInvalidMaximumScore() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> service.createAssignment(lessonId, teacherId, "Задание", null, null, 0));
        assertThat(assignments.findAllByLesson_IdOrderByIdAsc(lessonId)).isEmpty();
    }
}
