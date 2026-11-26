package ru.mephi.learnflow.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.dto.CourseDetails;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.Enrollment;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.exception.CourseInUseException;
import ru.mephi.learnflow.exception.ResourceNotFoundException;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.CourseRepository;
import ru.mephi.learnflow.repository.EnrollmentRepository;
import ru.mephi.learnflow.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseServiceTest {

    @Autowired
    private CourseService service;

    @Autowired
    private CourseRepository courses;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private EnrollmentRepository enrollments;

    @Autowired
    private EntityManager entities;

    private User teacher;
    private Category category;

    @BeforeEach
    void saveCourseReferences() {
        teacher = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER));
        category = categories.save(new Category("Базы данных"));
    }

    @Test
    void createsReadsUpdatesAndDeletesCourse() {
        CourseDetails created = service.createCourse("ORM", null, category.getId(), teacher.getId());
        entities.clear();
        assertThat(service.getCourse(created.id())).isEqualTo(created);

        User replacement = users.save(new User("Ирина", "irina@example.org", UserRole.TEACHER));
        Category newCategory = categories.save(new Category("Разработка"));
        CourseDetails updated = service.updateCourse(
                created.id(), "Hibernate", "Связи JPA", newCategory.getId(), replacement.getId());
        entities.flush();
        entities.clear();

        assertThat(updated).isEqualTo(
                new CourseDetails(created.id(), "Hibernate", "Связи JPA", newCategory.getId(), replacement.getId()));
        assertThat(service.getCourse(created.id())).isEqualTo(updated);
        assertThat(service.listCourses()).contains(updated);

        service.deleteCourse(created.id());
        assertThatThrownBy(() -> service.getCourse(created.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsStudentAsCourseTeacher() {
        User student = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT));

        assertThatIllegalArgumentException().isThrownBy(
                () -> service.createCourse("ORM", null, category.getId(), student.getId()));
    }

    @Test
    void rejectsDeletingCourseWithEnrollment() {
        CourseDetails created = service.createCourse("ORM", null, category.getId(), teacher.getId());
        Course course = courses.findById(created.id()).orElseThrow();
        User student = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT));
        enrollments.saveAndFlush(new Enrollment(student, course));

        assertThatThrownBy(() -> service.deleteCourse(created.id())).isInstanceOf(CourseInUseException.class);
        assertThat(courses.existsById(created.id())).isTrue();
    }
}
