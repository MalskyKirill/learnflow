package ru.mephi.learnflow.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.Enrollment;
import ru.mephi.learnflow.entity.EnrollmentStatus;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.EnrollmentRepository;
import ru.mephi.learnflow.repository.UserRepository;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class EnrollmentServiceTest {

    @Autowired
    private EnrollmentService service;

    @Autowired
    private EnrollmentRepository enrollments;

    @Autowired
    private CourseService courses;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Test
    void createsOnlyOneEnrollmentWhenTwoRequestsStartTogether() throws Exception {
        Category category = categories.save(new Category("Базы данных"));
        User teacher = users.save(new User("Мария", "enrollment-teacher@example.org", UserRole.TEACHER));
        User student = users.save(new User("Анна", "enrollment-student@example.org", UserRole.STUDENT));
        Long courseId = courses.createCourse("ORM", null, category.getId(), teacher.getId()).id();

        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Boolean> attempt = () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                EnrollmentDetails created = service.enrollStudent(courseId, student.getId());
                assertThat(created.status()).isEqualTo(EnrollmentStatus.ACTIVE);
                assertThat(created.courseId()).isEqualTo(courseId);
                assertThat(created.studentId()).isEqualTo(student.getId());
                assertThat(created.enrolledAt()).isNotNull();
                return true;
            } catch (AlreadyEnrolledException exception) {
                return false;
            }
        };

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);

            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(enrollments.findAllByCourse_Id(courseId)).extracting(Enrollment::getStatus)
                    .containsExactly(EnrollmentStatus.ACTIVE);
        } finally {
            enrollments.deleteAll(enrollments.findAllByCourse_Id(courseId));
            courses.deleteCourse(courseId);
            users.deleteById(student.getId());
            users.deleteById(teacher.getId());
            categories.deleteById(category.getId());
        }
    }

    @Test
    @Transactional
    void rejectsTeacherEnrollment() {
        Category category = categories.save(new Category("Базы данных"));
        User teacher = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER));
        Long courseId = courses.createCourse("ORM", null, category.getId(), teacher.getId()).id();

        assertThatIllegalArgumentException().isThrownBy(() -> service.enrollStudent(courseId, teacher.getId()));
    }

    @Test
    void rejectsEnrollmentInUnknownCourse() {
        assertThatThrownBy(() -> service.enrollStudent(Long.MAX_VALUE, Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Курс");
    }
}
