package ru.mephi.learnflow.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoursePersistenceTest {

    @Autowired
    private EntityManager entities;

    @Test
    void coursesShareCategoryAndTeacherAndLoadThemOnDemand() {
        Category category = new Category("Базы данных");
        User teacher = new User("Мария", "maria@example.org", UserRole.TEACHER);
        entities.persist(category);
        entities.persist(teacher);
        Course sql = new Course("SQL", "Основы запросов", category, teacher);
        Course orm = new Course("ORM", "Связи сущностей", category, teacher);
        entities.persist(sql);
        entities.persist(orm);
        entities.flush();
        entities.clear();

        Course first = entities.find(Course.class, sql.getId());
        Course second = entities.find(Course.class, orm.getId());
        PersistenceUnitUtil persistence = entities.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(persistence.isLoaded(first, "teacher")).isFalse();
        assertThat(persistence.isLoaded(first, "category")).isFalse();
        assertThat(first.getTitle()).isEqualTo("SQL");
        assertThat(second.getDescription()).isEqualTo("Связи сущностей");
        assertThat(first.getTeacher().getEmail()).isEqualTo("maria@example.org");
        assertThat(first.getCategory().getName()).isEqualTo("Базы данных");
        assertThat(second.getTeacher().getId()).isEqualTo(first.getTeacher().getId());
        assertThat(second.getCategory().getId()).isEqualTo(first.getCategory().getId());
    }

    @Test
    void loadsModulesAndLessonsLazilyInStoredPositionOrder() {
        Category category = new Category("Разработка");
        User teacher = new User("Ирина", "irina@example.org", UserRole.TEACHER);
        entities.persist(category);
        entities.persist(teacher);
        Course course = new Course("Hibernate", null, category, teacher);
        CourseModule practice = course.addModule("Практика");
        CourseModule theory = course.addModule("Теория");
        Lesson queries = theory.addLesson("Запросы", "Введение в JPQL");
        theory.addLesson("Сущности", "Отображение таблиц");
        practice.addLesson("Проект", "Создайте модель");
        entities.persist(course);
        entities.flush();

        // Отличаем порядок программы от порядка вставки строк в базу.
        entities.createQuery("update CourseModule m set m.position = 3 where m.id = :id")
                .setParameter("id", practice.getId()).executeUpdate();
        entities.createQuery("update Lesson l set l.position = 3 where l.id = :id")
                .setParameter("id", queries.getId()).executeUpdate();
        entities.clear();

        Course loaded = entities.find(Course.class, course.getId());
        PersistenceUnitUtil persistence = entities.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(persistence.isLoaded(loaded, "modules")).isFalse();
        assertThat(loaded.getModules()).extracting(CourseModule::getTitle)
                .containsExactly("Теория", "Практика");

        CourseModule first = loaded.getModules().getFirst();
        assertThat(persistence.isLoaded(first, "lessons")).isFalse();
        assertThat(first.getLessons()).extracting(Lesson::getTitle)
                .containsExactly("Сущности", "Запросы");
        assertThat(first.getLessons().getLast().getContent()).isEqualTo("Введение в JPQL");
        assertThat(loaded.getModules().getLast().getLessons()).extracting(Lesson::getTitle)
                .containsExactly("Проект");
    }

    @Test
    void rejectsStudentAsTeacher() {
        User student = new User("Иван", "ivan@example.org", UserRole.STUDENT);

        assertThatIllegalArgumentException().isThrownBy(
                () -> new Course("SQL", null, new Category("Базы данных"), student));
    }
}
