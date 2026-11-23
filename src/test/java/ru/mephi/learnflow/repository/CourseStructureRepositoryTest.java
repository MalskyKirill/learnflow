package ru.mephi.learnflow.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.CourseModule;
import ru.mephi.learnflow.entity.Lesson;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

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
    private EntityManager entities;

    @Test
    void savesReadsAndDeletesCourseStructureThroughRepositories() {
        User teacher = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER));
        Category category = categories.save(new Category("Базы данных"));
        Course course = new Course("Hibernate", "Практика работы с ORM", category, teacher);
        CourseModule basics = course.addModule("Основы");
        basics.addLesson("Сущности", "Разметка JPA");
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

        courses.delete(loaded);
        courses.flush();
        entities.clear();

        assertThat(courses.findById(courseId)).isEmpty();
        assertThat(modules.findAllByCourse_IdOrderByPositionAsc(courseId)).isEmpty();
        assertThat(lessons.findAllByModule_IdOrderByPositionAsc(basics.getId())).isEmpty();
        assertThat(lessons.findAllByModule_IdOrderByPositionAsc(queries.getId())).isEmpty();
        assertThat(users.findById(teacher.getId())).isPresent();
        assertThat(categories.findById(category.getId())).isPresent();
    }
}
