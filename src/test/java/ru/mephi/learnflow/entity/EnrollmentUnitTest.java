package ru.mephi.learnflow.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.mephi.learnflow.exception.InvalidInputException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentUnitTest {

    private final User teacher = new User("Мария", "teacher@example.org", UserRole.TEACHER);
    private final Course course = new Course("Основы JPA", null, new Category("Базы данных"), teacher);

    @Test
    void newEnrollmentIsActiveAndLinksStudentToCourse() {
        User student = new User("Анна", "student@example.org", UserRole.STUDENT);

        Enrollment enrollment = new Enrollment(student, course);

        assertThat(enrollment.getStudent()).isSameAs(student);
        assertThat(enrollment.getCourse()).isSameAs(course);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "STUDENT", mode = EnumSource.Mode.EXCLUDE)
    void enrollmentRequiresStudentRole(UserRole role) {
        User candidate = new User("Кандидат", "candidate@example.org", role);

        assertThatThrownBy(() -> new Enrollment(candidate, course))
                .isInstanceOf(InvalidInputException.class);
    }
}
