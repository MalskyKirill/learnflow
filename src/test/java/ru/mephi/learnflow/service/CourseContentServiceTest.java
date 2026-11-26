package ru.mephi.learnflow.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.dto.CourseDetails;
import ru.mephi.learnflow.dto.CourseModuleDetails;
import ru.mephi.learnflow.dto.CourseProgram;
import ru.mephi.learnflow.dto.LessonDetails;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.exception.ResourceNotFoundException;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseContentServiceTest {

    @Autowired
    private CourseContentService content;

    @Autowired
    private CourseService courses;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entities;

    @Test
    void appendsModulesInCourseOrderAndKeepsCoursesSeparate() {
        Category category = categories.save(new Category("Базы данных"));
        User teacher = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER));
        Long firstCourseId = courses.createCourse("ORM", null, category.getId(), teacher.getId()).id();
        Long secondCourseId = courses.createCourse("SQL", null, category.getId(), teacher.getId()).id();
        assertThat(content.listModules(firstCourseId)).isEmpty();

        CourseModuleDetails basics = content.addModule(firstCourseId, "Основы");
        CourseModuleDetails queries = content.addModule(secondCourseId, "Запросы");
        entities.flush();
        entities.clear();
        CourseModuleDetails relations = content.addModule(firstCourseId, "Связи");
        entities.flush();
        entities.clear();

        assertThat(basics.position()).isEqualTo(1);
        assertThat(relations.position()).isEqualTo(2);
        assertThat(queries.position()).isEqualTo(1);
        assertThat(content.listModules(firstCourseId)).containsExactly(basics, relations);
        assertThat(content.listModules(secondCourseId)).containsExactly(queries);
    }

    @Test
    void loadsProgramWithOrderedLessonsAndEmptyModules() {
        Category category = categories.save(new Category("Базы данных"));
        User teacher = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER));
        CourseDetails course = courses.createCourse("ORM", null, category.getId(), teacher.getId());
        assertThat(content.getProgram(course.id()).modules()).isEmpty();
        CourseModuleDetails basics = content.addModule(course.id(), "Основы");
        CourseModuleDetails queries = content.addModule(course.id(), "Запросы");
        CourseModuleDetails practice = content.addModule(course.id(), "Практика");
        LessonDetails mapping = content.addLesson(basics.id(), "Сущности", "Разметка JPA");
        LessonDetails relations = content.addLesson(basics.id(), "Связи", "Внешние ключи");
        LessonDetails jpql = content.addLesson(queries.id(), "JPQL", null);
        entities.flush();
        entities.clear();

        CourseProgram program = content.getProgram(course.id());

        assertThat(program.course()).isEqualTo(course);
        assertThat(program.modules()).containsExactly(
                new CourseProgram.Module(basics, List.of(mapping, relations)),
                new CourseProgram.Module(queries, List.of(jpql)),
                new CourseProgram.Module(practice, List.of()));
        assertThat(mapping.position()).isEqualTo(1);
        assertThat(relations.position()).isEqualTo(2);
        assertThat(jpql.position()).isEqualTo(1);
    }

    @Test
    void rejectsAddingLessonToUnknownModule() {
        assertThatThrownBy(() -> content.addLesson(Long.MAX_VALUE, "Сущности", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsAddingModuleToUnknownCourse() {
        assertThatThrownBy(() -> content.addModule(Long.MAX_VALUE, "Основы"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsBlankModuleTitle() {
        Category category = categories.save(new Category("Базы данных"));
        User teacher = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER));
        Long courseId = courses.createCourse("ORM", null, category.getId(), teacher.getId()).id();

        assertThatIllegalArgumentException().isThrownBy(() -> content.addModule(courseId, " "));
    }
}
