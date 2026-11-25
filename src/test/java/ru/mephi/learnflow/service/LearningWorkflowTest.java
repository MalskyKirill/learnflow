package ru.mephi.learnflow.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.EnrollmentStatus;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.SubmissionRepository;
import ru.mephi.learnflow.repository.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LearningWorkflowTest {

    @Autowired
    private CourseService courses;

    @Autowired
    private CourseContentService content;

    @Autowired
    private EnrollmentService enrollments;

    @Autowired
    private AssignmentService assignments;

    @Autowired
    private SubmissionService submissions;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    private Long categoryId;
    private Long teacherId;
    private Long studentId;
    private Long courseId;
    private Long submissionId;

    @Test
    void studentReceivesGradeForAssignmentFromCourseTeacher() {
        categoryId = categories.save(new Category("ORM и Hibernate")).getId();
        teacherId = users.save(new User("Мария", "workflow-teacher@example.org", UserRole.TEACHER)).getId();
        studentId = users.save(new User("Анна", "workflow-student@example.org", UserRole.STUDENT)).getId();

        CourseDetails course = courses.createCourse("Hibernate", "Введение в ORM", categoryId, teacherId);
        courseId = course.id();
        CourseModuleDetails module = content.addModule(courseId, "Основы JPA");
        LessonDetails lesson = content.addLesson(module.id(), "Связи", "Внешние ключи и связи сущностей");
        assertThat(content.getProgram(courseId))
                .isEqualTo(new CourseProgram(course, List.of(new CourseProgram.Module(module, List.of(lesson)))));

        AssignmentDetails assignment = assignments.createAssignment(
                lesson.id(), teacherId, "Смоделируй связь", "Опиши связь курса и преподавателя", null, 20);
        EnrollmentDetails enrollment = enrollments.enrollStudent(courseId, studentId);
        assertThat(enrollment.status()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(enrollments.listCourses(studentId)).containsExactly(course);

        SubmissionDetails submitted = submissions.submitAssignment(
                assignment.id(), studentId, "Курс ссылается на преподавателя через ManyToOne");
        submissionId = submitted.id();
        assertThat(submitted.score()).isNull();
        assertThat(submissions.listAssignmentSubmissions(assignment.id(), teacherId)).containsExactly(submitted);

        SubmissionDetails graded = submissions.gradeSubmission(submissionId, teacherId, 19, "Добавь пример схемы");
        assertThat(graded.score()).isEqualTo(19);
        assertThat(graded.feedback()).isEqualTo("Добавь пример схемы");
        assertThat(submissions.listStudentSubmissions(studentId)).containsExactly(graded);
    }

    @AfterEach
    void removeFixtures() {
        if (submissionId != null) {
            submissionRepository.deleteById(submissionId);
        }
        if (courseId != null) {
            enrollments.unenrollStudent(courseId, studentId);
            courses.deleteCourse(courseId);
        }
        if (studentId != null) {
            users.deleteById(studentId);
        }
        if (teacherId != null) {
            users.deleteById(teacherId);
        }
        if (categoryId != null) {
            categories.deleteById(categoryId);
        }
    }
}
