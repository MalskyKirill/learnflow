package ru.mephi.learnflow.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.SubmissionRepository;
import ru.mephi.learnflow.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubmissionServiceTest {

    @Autowired
    private SubmissionService service;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private EnrollmentService enrollments;

    @Autowired
    private CourseService courses;

    @Autowired
    private CourseContentService content;

    @Autowired
    private AssignmentService assignments;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entities;

    private Long assignmentId;
    private Long courseId;
    private Long studentId;

    @BeforeEach
    void createAssignmentAndStudent() {
        Long teacherId = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER)).getId();
        studentId = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT)).getId();
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        courseId = courses.createCourse("ORM", null, categoryId, teacherId).id();
        Long moduleId = content.addModule(courseId, "Основы").id();
        Long lessonId = content.addLesson(moduleId, "Связи", null).id();
        assignmentId = assignments.createAssignment(lessonId, teacherId, "JPA-связи", null, null, 20).id();
        entities.clear();
    }

    @Test
    void storesSubmissionFromEnrolledStudent() {
        enrollments.enrollStudent(courseId, studentId);

        SubmissionDetails submitted = service.submitAssignment(assignmentId, studentId, "Связь многие к одному");
        entities.clear();

        assertThat(submitted.id()).isNotNull();
        assertThat(submitted.assignmentId()).isEqualTo(assignmentId);
        assertThat(submitted.studentId()).isEqualTo(studentId);
        assertThat(submitted.submittedAt()).isNotNull();
        assertThat(submitted.score()).isNull();
        assertThat(submissions.findById(submitted.id())).get()
                .extracting(SubmissionDetails::from).isEqualTo(submitted);
    }

    @Test
    void rejectsSubmissionAfterUnenrollment() {
        enrollments.enrollStudent(courseId, studentId);
        enrollments.unenrollStudent(courseId, studentId);

        assertThatThrownBy(() -> service.submitAssignment(assignmentId, studentId, "Решение"))
                .isInstanceOf(EnrollmentRequiredException.class);
        assertThat(submissions.findAllByAssignment_Id(assignmentId)).isEmpty();
    }

    @Test
    void reportsDuplicateSubmission() {
        enrollments.enrollStudent(courseId, studentId);
        service.submitAssignment(assignmentId, studentId, "Первое решение");

        assertThatThrownBy(() -> service.submitAssignment(assignmentId, studentId, "Повторная отправка"))
                .isInstanceOf(AlreadySubmittedException.class);
    }

    @Test
    void rejectsBlankSubmission() {
        enrollments.enrollStudent(courseId, studentId);

        assertThatIllegalArgumentException().isThrownBy(() -> service.submitAssignment(assignmentId, studentId, " "));
        assertThat(submissions.findAllByAssignment_Id(assignmentId)).isEmpty();
    }
}
