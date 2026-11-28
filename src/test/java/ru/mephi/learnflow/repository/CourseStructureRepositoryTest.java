package ru.mephi.learnflow.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Assignment;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.CourseModule;
import ru.mephi.learnflow.entity.Lesson;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseStructureRepositoryTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private CourseRepository courses;

    @Autowired
    private CourseModuleRepository modules;

    @Autowired
    private LessonRepository lessons;

    @Autowired
    private AssignmentRepository assignments;

    @Autowired
    private EntityManager entities;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savesReadsAndDeletesCourseStructureThroughRepositories() {
        User teacher = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER));
        Category category = categories.save(new Category("Базы данных"));
        Course course = new Course("Hibernate", "Практика работы с ORM", category, teacher);
        CourseModule basics = course.addModule("Основы");
        Assignment assignment = basics.addLesson("Сущности", "Разметка JPA")
                .addAssignment("Связи JPA", null, null, 20);
        basics.addLesson("Связи", "Внешние ключи");
        CourseModule queries = course.addModule("Запросы");
        queries.addLesson("JPQL", "Запросы к сущностям");

        Long courseId = courses.saveAndFlush(course).getId();
        entities.clear();

        Course loaded = courses.findById(courseId).orElseThrow();
        assertThat(loaded.getTeacher().getId()).isEqualTo(teacher.getId());
        assertThat(loaded.getCategory().getId()).isEqualTo(category.getId());
        assertThat(modules.findAllByCourse_IdOrderByPositionAsc(courseId))
                .extracting(CourseModule::getTitle).containsExactly("Основы", "Запросы");
        assertThat(lessons.findAllByModule_IdOrderByPositionAsc(basics.getId()))
                .extracting(Lesson::getTitle).containsExactly("Сущности", "Связи");
        assertThat(lessons.findAllByModule_IdOrderByPositionAsc(queries.getId()))
                .extracting(Lesson::getTitle).containsExactly("JPQL");
        assertThat(assignments.findById(assignment.getId())).get()
                .extracting(Assignment::getTitle).isEqualTo("Связи JPA");

        courses.delete(loaded);
        courses.flush();
        entities.clear();

        assertThat(courses.findById(courseId)).isEmpty();
        assertThat(modules.findAllByCourse_IdOrderByPositionAsc(courseId)).isEmpty();
        assertThat(lessons.findAllByModule_IdOrderByPositionAsc(basics.getId())).isEmpty();
        assertThat(lessons.findAllByModule_IdOrderByPositionAsc(queries.getId())).isEmpty();
        assertThat(assignments.findById(assignment.getId())).isEmpty();
        assertThat(users.findById(teacher.getId())).isPresent();
        assertThat(categories.findById(category.getId())).isPresent();
    }

    @Test
    void modulePositionsAreUniqueWithinEachCourse() {
        Course first = saveCourse();
        CourseModule basics = first.addModule("Основы");
        Course second = new Course("SQL", null, first.getCategory(), first.getTeacher());
        CourseModule queries = second.addModule("Запросы");
        courses.saveAndFlush(second);

        assertThat(modules.findById(queries.getId())).get()
                .extracting(CourseModule::getPosition).isEqualTo(basics.getPosition());
        assertThatThrownBy(() -> jdbc.update("""
                insert into course_modules (course_id, title, position) values (?, ?, ?)
                """, first.getId(), "Повторная позиция", basics.getPosition()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void lessonPositionsAreUniqueWithinEachModule() {
        Course course = saveCourse();
        CourseModule basics = course.addModule("Основы");
        Lesson mapping = basics.addLesson("Сущности", null);
        Lesson jpql = course.addModule("Запросы").addLesson("JPQL", null);
        courses.flush();

        assertThat(lessons.findById(jpql.getId())).get()
                .extracting(Lesson::getPosition).isEqualTo(mapping.getPosition());
        assertThatThrownBy(() -> jdbc.update("""
                insert into lessons (module_id, title, position) values (?, ?, ?)
                """, basics.getId(), "Повторная позиция", mapping.getPosition()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void categoryNamesAreUnique() {
        Category category = categories.saveAndFlush(new Category("Базы данных"));

        assertThatThrownBy(() -> jdbc.update("insert into categories (name) values (?)", category.getName()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private Course saveCourse() {
        Category category = categories.save(new Category("Базы данных"));
        User teacher = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER));
        return courses.save(new Course("Hibernate", null, category, teacher));
    }
}
