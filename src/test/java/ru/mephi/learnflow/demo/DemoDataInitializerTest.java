package ru.mephi.learnflow.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import ru.mephi.learnflow.dto.AssignmentDetails;
import ru.mephi.learnflow.dto.CourseDetails;
import ru.mephi.learnflow.dto.CourseProgram;
import ru.mephi.learnflow.dto.EnrollmentDetails;
import ru.mephi.learnflow.dto.QuizAttemptDetails;
import ru.mephi.learnflow.dto.QuizView;
import ru.mephi.learnflow.dto.SubmissionDetails;
import ru.mephi.learnflow.entity.EnrollmentStatus;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.ProfileRepository;
import ru.mephi.learnflow.repository.UserRepository;
import ru.mephi.learnflow.security.LearnFlowPrincipal;
import ru.mephi.learnflow.service.AssignmentService;
import ru.mephi.learnflow.service.CourseContentService;
import ru.mephi.learnflow.service.CourseService;
import ru.mephi.learnflow.service.EnrollmentService;
import ru.mephi.learnflow.service.QuizService;
import ru.mephi.learnflow.service.SubmissionService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///learnflow_demo_test",
        "DEMO_TEACHER_PASSWORD=test-teacher-password",
        "DEMO_STUDENT_PASSWORD=test-student-password"
})
@ActiveProfiles({"test", "demo"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoDataInitializerTest {

    @Autowired
    private DemoDataInitializer initializer;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserDetailsService userDetails;

    @Autowired
    private PasswordEncoder passwords;

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
    private QuizService quizzes;

    @Test
    void createsAccountsThatCanLogInAndSeededCategory() {
        assertAccount(DemoDataInitializer.TEACHER_EMAIL, UserRole.TEACHER, "test-teacher-password");
        assertAccount(DemoDataInitializer.STUDENT_EMAIL, UserRole.STUDENT, "test-student-password");
        assertThat(categories.findByName(DemoDataInitializer.CATEGORY_NAME)).isPresent();
    }

    @Test
    void rerunningInitializerKeepsExistingDemoData() {
        User teacher = users.findByEmail(DemoDataInitializer.TEACHER_EMAIL).orElseThrow();
        Long categoryId = categories.findByName(DemoDataInitializer.CATEGORY_NAME).orElseThrow().getId();

        initializer.run(new DefaultApplicationArguments());

        assertThat(users.count()).isEqualTo(2);
        assertThat(profiles.count()).isEqualTo(2);
        assertThat(categories.count()).isOne();
        User existingTeacher = users.findByEmail(DemoDataInitializer.TEACHER_EMAIL).orElseThrow();
        assertThat(existingTeacher.getId()).isEqualTo(teacher.getId());
        assertThat(existingTeacher.getPasswordHash()).isEqualTo(teacher.getPasswordHash());
        assertThat(categories.findByName(DemoDataInitializer.CATEGORY_NAME).orElseThrow().getId())
                .isEqualTo(categoryId);
    }

    @Test
    void demoCourseIsReadyToUseAndRerunningInitializerPreservesProgress() {
        Long teacherId = users.findByEmail(DemoDataInitializer.TEACHER_EMAIL).orElseThrow().getId();
        Long studentId = users.findByEmail(DemoDataInitializer.STUDENT_EMAIL).orElseThrow().getId();
        Long categoryId = categories.findByName(DemoDataInitializer.CATEGORY_NAME).orElseThrow().getId();
        assertThat(courses.listCourses()).singleElement().satisfies(course -> {
            assertThat(course.title()).isEqualTo(DemoCourseInitializer.COURSE_TITLE);
            assertThat(course.teacherId()).isEqualTo(teacherId);
            assertThat(course.categoryId()).isEqualTo(categoryId);
        });
        CourseDetails course = courses.listCourses().getFirst();
        EnrollmentDetails enrollment = enrollments.getEnrollment(course.id(), studentId);
        assertThat(enrollment.status()).isEqualTo(EnrollmentStatus.ACTIVE);

        CourseProgram program = content.getProgram(course.id());
        assertThat(program.modules()).hasSize(1);
        CourseProgram.Module module = program.modules().getFirst();
        assertThat(module.lessons()).hasSize(1);
        Long lessonId = module.lessons().getFirst().id();
        assertThat(assignments.listAssignments(lessonId)).singleElement()
                .extracting(AssignmentDetails::maxScore).isEqualTo(20);
        AssignmentDetails assignment = assignments.listAssignments(lessonId).getFirst();
        SubmissionDetails submitted = submissions.submitAssignment(assignment.id(), studentId,
                "В таблице lessons хранится внешний ключ на модуль. В сущности используется @ManyToOne.");
        submissions.gradeSubmission(submitted.id(), teacherId, 18, "Укажи имя внешнего ключа");

        QuizView quiz = quizzes.getQuizByModule(module.details().id(), studentId);
        assertThat(quiz.questions()).hasSize(2);
        QuizView.Question association = quiz.questions().getFirst();
        QuizView.Question fetchMode = quiz.questions().getLast();
        QuizAttemptDetails attempt = quizzes.submitQuiz(quiz.quiz().id(), studentId,
                Map.of(association.id(), optionId(association, "@ManyToOne"),
                        fetchMode.id(), optionId(fetchMode, "LAZY")));
        assertThat(attempt.score()).isEqualTo(2);

        initializer.run(new DefaultApplicationArguments());

        assertThat(courses.listCourses()).containsExactly(course);
        assertThat(content.getProgram(course.id())).isEqualTo(program);
        assertThat(assignments.listAssignments(lessonId)).containsExactly(assignment);
        assertThat(enrollments.getEnrollment(course.id(), studentId)).isEqualTo(enrollment);
        assertThat(submissions.listStudentSubmissions(studentId)).singleElement().satisfies(grade -> {
            assertThat(grade.id()).isEqualTo(submitted.id());
            assertThat(grade.score()).isEqualTo(18);
        });
        assertThat(quizzes.listStudentAttempts(studentId)).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo(attempt.id());
            assertThat(result.score()).isEqualTo(2);
            assertThat(result.answers()).containsExactlyInAnyOrderElementsOf(attempt.answers());
        });
    }

    private Long optionId(QuizView.Question question, String text) {
        return question.options().stream()
                .filter(option -> option.text().equals(text))
                .findFirst().orElseThrow().id();
    }

    private void assertAccount(String email, UserRole role, String rawPassword) {
        User account = users.findByEmail(email).orElseThrow();
        assertThat(account.getRole()).isEqualTo(role);
        assertThat(profiles.findById(account.getId())).isPresent();

        UserDetails principal = userDetails.loadUserByUsername(email);
        assertThat(principal).isInstanceOfSatisfying(LearnFlowPrincipal.class,
                authenticated -> assertThat(authenticated.getUserId()).isEqualTo(account.getId()));
        assertThat(principal.getPassword()).isNotEqualTo(rawPassword);
        assertThat(passwords.matches(rawPassword, principal.getPassword())).isTrue();
    }
}
