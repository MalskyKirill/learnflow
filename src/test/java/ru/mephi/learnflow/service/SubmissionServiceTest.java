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
    private Long lessonId;
    private Long courseId;
    private Long studentId;
    private Long teacherId;

    @BeforeEach
    void createAssignmentAndStudent() {
        teacherId = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER)).getId();
        studentId = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT)).getId();
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        courseId = courses.createCourse("ORM", null, categoryId, teacherId).id();
        Long moduleId = content.addModule(courseId, "Основы").id();
        lessonId = content.addLesson(moduleId, "Связи", null).id();
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

    @Test
    void assignedTeacherCanGradeAndUpdateTheGrade() {
        enrollments.enrollStudent(courseId, studentId);
        SubmissionDetails submitted = service.submitAssignment(assignmentId, studentId, "Связь многие к одному");
        entities.clear();

        SubmissionDetails graded = service.gradeSubmission(submitted.id(), teacherId, 0, "Нужен пример");
        assertThat(graded.score()).isZero();
        assertThat(graded.feedback()).isEqualTo("Нужен пример");
        entities.flush();
        entities.clear();
        assertThat(submissions.findById(submitted.id())).get()
                .extracting(SubmissionDetails::from).isEqualTo(graded);

        SubmissionDetails regraded = service.gradeSubmission(submitted.id(), teacherId, 20, "Верное решение");
        entities.flush();
        entities.clear();

        assertThat(regraded).isEqualTo(new SubmissionDetails(submitted.id(), assignmentId, studentId,
                submitted.content(), submitted.submittedAt(), 20, "Верное решение"));
        assertThat(submissions.findById(submitted.id())).get()
                .extracting(SubmissionDetails::from).isEqualTo(regraded);
    }

    @Test
    void rejectsGradeFromAnotherTeacherAndStudent() {
        enrollments.enrollStudent(courseId, studentId);
        Long submissionId = service.submitAssignment(assignmentId, studentId, "Решение").id();
        Long otherTeacherId = users.save(new User("Ирина", "irina@example.org", UserRole.TEACHER)).getId();

        assertThatThrownBy(() -> service.gradeSubmission(submissionId, otherTeacherId, 10, null))
                .isInstanceOf(CourseTeacherRequiredException.class);
        assertThatThrownBy(() -> service.gradeSubmission(submissionId, studentId, 10, null))
                .isInstanceOf(CourseTeacherRequiredException.class);
        assertThat(submissions.findById(submissionId).orElseThrow().getScore()).isNull();
    }

    @Test
    void rejectsScoreOutsideAssignmentRange() {
        enrollments.enrollStudent(courseId, studentId);
        Long submissionId = service.submitAssignment(assignmentId, studentId, "Решение").id();

        assertThatIllegalArgumentException().isThrownBy(
                () -> service.gradeSubmission(submissionId, teacherId, -1, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> service.gradeSubmission(submissionId, teacherId, 21, null));
        assertThat(submissions.findById(submissionId).orElseThrow().getScore()).isNull();
    }

    @Test
    void listsAssignmentSubmissionsAndStudentsOwnGradedHistory() {
        Long secondStudentId = users.save(new User("Иван", "ivan@example.org", UserRole.STUDENT)).getId();
        Long secondAssignmentId = assignments.createAssignment(lessonId, teacherId, "JPQL", null, null, 10).id();
        enrollments.enrollStudent(courseId, studentId);
        enrollments.enrollStudent(courseId, secondStudentId);
        assertThat(service.listAssignmentSubmissions(assignmentId, teacherId)).isEmpty();
        assertThat(service.listStudentSubmissions(studentId)).isEmpty();

        SubmissionDetails first = service.submitAssignment(assignmentId, studentId, "Связь многие к одному");
        SubmissionDetails second = service.submitAssignment(assignmentId, secondStudentId, "Связь один ко многим");
        SubmissionDetails third = service.submitAssignment(secondAssignmentId, studentId, "select c from Course c");
        SubmissionDetails graded = service.gradeSubmission(first.id(), teacherId, 18, "Добавь пример");
        enrollments.unenrollStudent(courseId, studentId);
        entities.flush();
        entities.clear();

        assertThat(service.listAssignmentSubmissions(assignmentId, teacherId)).containsExactly(second, graded);
        assertThat(service.listStudentSubmissions(studentId)).containsExactly(third, graded);
        assertThat(service.listStudentSubmissions(secondStudentId)).containsExactly(second);
    }

    @Test
    void rejectsAssignmentSubmissionListForAnotherTeacherAndStudent() {
        Long otherTeacherId = users.save(new User("Ирина", "irina@example.org", UserRole.TEACHER)).getId();

        assertThatThrownBy(() -> service.listAssignmentSubmissions(assignmentId, otherTeacherId))
                .isInstanceOf(CourseTeacherRequiredException.class);
        assertThatThrownBy(() -> service.listAssignmentSubmissions(assignmentId, studentId))
                .isInstanceOf(CourseTeacherRequiredException.class);
    }
}
