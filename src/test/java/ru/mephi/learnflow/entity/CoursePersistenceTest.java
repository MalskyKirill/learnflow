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
    void rejectsStudentAsTeacher() {
        User student = new User("Иван", "ivan@example.org", UserRole.STUDENT);

        assertThatIllegalArgumentException().isThrownBy(
                () -> new Course("SQL", null, new Category("Базы данных"), student));
    }
}
