package ru.mephi.learnflow.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.Enrollment;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnrollmentRepositoryTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private CourseRepository courses;

    @Autowired
    private EnrollmentRepository enrollments;

    @Autowired
    private EntityManager entities;

    @Test
    void findsEnrollmentsByCourseAndStudentAndDeletesOnlySelectedEnrollment() {
        User teacher = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER));
        User anna = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT));
        User ivan = users.save(new User("Иван", "ivan@example.org", UserRole.STUDENT));
        Category category = categories.save(new Category("Базы данных"));
        Course orm = courses.save(new Course("ORM", null, category, teacher));
        Course sql = courses.save(new Course("SQL", null, category, teacher));
        Enrollment annaOrm = enrollments.save(new Enrollment(anna, orm));
        Enrollment annaSql = enrollments.save(new Enrollment(anna, sql));
        Enrollment ivanOrm = enrollments.saveAndFlush(new Enrollment(ivan, orm));
        entities.clear();

        assertThat(users.findByEmail("anna@example.org")).get()
                .extracting(User::getId).isEqualTo(anna.getId());
        assertThat(courses.findAllByCategory_Id(category.getId())).extracting(Course::getId)
                .containsExactlyInAnyOrder(orm.getId(), sql.getId());
        assertThat(enrollments.findAllByStudent_Id(anna.getId())).extracting(Enrollment::getId)
                .containsExactlyInAnyOrder(annaOrm.getId(), annaSql.getId());
        assertThat(enrollments.findAllByCourse_Id(orm.getId())).extracting(Enrollment::getId)
                .containsExactlyInAnyOrder(annaOrm.getId(), ivanOrm.getId());

        Enrollment selected = enrollments.findByStudent_IdAndCourse_Id(anna.getId(), orm.getId()).orElseThrow();
        enrollments.delete(selected);
        enrollments.flush();
        entities.clear();

        assertThat(enrollments.findByStudent_IdAndCourse_Id(anna.getId(), orm.getId())).isEmpty();
        assertThat(enrollments.findAllByStudent_Id(anna.getId())).extracting(Enrollment::getId)
                .containsExactly(annaSql.getId());
        assertThat(enrollments.findAllByCourse_Id(orm.getId())).extracting(Enrollment::getId)
                .containsExactly(ivanOrm.getId());
    }
}
