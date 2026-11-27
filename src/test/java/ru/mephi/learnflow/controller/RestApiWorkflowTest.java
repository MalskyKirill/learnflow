package ru.mephi.learnflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import ru.mephi.learnflow.dto.AssignmentDetails;
import ru.mephi.learnflow.dto.CourseDetails;
import ru.mephi.learnflow.dto.CourseModuleDetails;
import ru.mephi.learnflow.dto.EnrollmentDetails;
import ru.mephi.learnflow.dto.LessonDetails;
import ru.mephi.learnflow.dto.QuizAttemptDetails;
import ru.mephi.learnflow.dto.QuizDetails;
import ru.mephi.learnflow.dto.QuizQuestionDetails;
import ru.mephi.learnflow.dto.QuizView;
import ru.mephi.learnflow.dto.SubmissionDetails;
import ru.mephi.learnflow.dto.request.CreateAssignmentRequest;
import ru.mephi.learnflow.dto.request.CreateCourseRequest;
import ru.mephi.learnflow.dto.request.CreateLessonRequest;
import ru.mephi.learnflow.dto.request.CreateModuleRequest;
import ru.mephi.learnflow.dto.request.CreateQuizQuestionRequest;
import ru.mephi.learnflow.dto.request.CreateQuizQuestionRequest.Option;
import ru.mephi.learnflow.dto.request.CreateQuizRequest;
import ru.mephi.learnflow.dto.request.CreateSubmissionRequest;
import ru.mephi.learnflow.dto.request.GradeSubmissionRequest;
import ru.mephi.learnflow.dto.request.SubmitQuizAttemptRequest;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.EnrollmentStatus;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.UserRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///learnflow_rest_workflow_test"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RestApiWorkflowTest {

    private static final String PASSWORD = "rest-workflow-test-password";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwords;

    @Test
    void studentSubmitsAssignmentAndReceivesTeachersGrade() {
        CourseScenario scenario = createCourseWithEnrolledStudent("assignments");
        AuthenticatedClient teacher = scenario.teacher();
        AuthenticatedClient student = scenario.student();
        LessonDetails lesson = create(teacher, "/api/modules/" + scenario.moduleId() + "/lessons",
                new CreateLessonRequest("Связи сущностей", "Введение в JPA"), LessonDetails.class);
        String assignmentsPath = "/api/lessons/" + lesson.id() + "/assignments";
        AssignmentDetails assignment = create(teacher, assignmentsPath,
                new CreateAssignmentRequest("Связи JPA", "Опиши связь урока с модулем", null, 20),
                AssignmentDetails.class);
        assertThat(body(student.http().getForEntity(assignmentsPath, AssignmentDetails[].class), HttpStatus.OK))
                .containsExactly(assignment);

        String submissionsPath = "/api/assignments/" + assignment.id() + "/submissions";
        SubmissionDetails submitted = create(student, submissionsPath,
                new CreateSubmissionRequest("Урок ссылается на модуль через ManyToOne"), SubmissionDetails.class);
        assertThat(submitted.score()).isNull();
        assertThat(body(teacher.http().getForEntity(submissionsPath, SubmissionDetails[].class), HttpStatus.OK))
                .containsExactly(submitted);

        GradeSubmissionRequest grade = new GradeSubmissionRequest(19, "Добавь пример внешнего ключа");
        SubmissionDetails graded = body(teacher.http().exchange("/api/submissions/" + submitted.id() + "/grade",
                HttpMethod.PUT, new HttpEntity<>(grade, teacher.writeHeaders()), SubmissionDetails.class),
                HttpStatus.OK);
        assertThat(graded.score()).isEqualTo(19);
        assertThat(graded.feedback()).isEqualTo(grade.feedback());
        assertThat(body(student.http().getForEntity("/api/me/submissions", SubmissionDetails[].class), HttpStatus.OK))
                .containsExactly(graded);
    }

    @Test
    void studentRetriesQuizAndBothRolesSeeTheResults() {
        CourseScenario scenario = createCourseWithEnrolledStudent("quizzes");
        AuthenticatedClient teacher = scenario.teacher();
        AuthenticatedClient student = scenario.student();
        String moduleQuizPath = "/api/modules/" + scenario.moduleId() + "/quiz";
        QuizDetails quiz = create(teacher, moduleQuizPath, new CreateQuizRequest("Связи и загрузка"), QuizDetails.class);
        String quizPath = "/api/quizzes/" + quiz.id();
        create(teacher, quizPath + "/questions", new CreateQuizQuestionRequest("Какая аннотация нужна?",
                List.of(new Option("@OneToMany", false), new Option("@ManyToOne", true))), QuizQuestionDetails.class);
        create(teacher, quizPath + "/questions", new CreateQuizQuestionRequest("Как отложить загрузку?",
                List.of(new Option("LAZY", true), new Option("EAGER", false))), QuizQuestionDetails.class);

        QuizView view = body(student.http().getForEntity(moduleQuizPath, QuizView.class), HttpStatus.OK);
        assertThat(view.quiz()).isEqualTo(quiz);
        assertThat(view.questions()).extracting(QuizView.Question::text)
                .containsExactly("Какая аннотация нужна?", "Как отложить загрузку?");
        QuizView.Question relation = view.questions().getFirst();
        QuizView.Question loading = view.questions().getLast();
        String attemptsPath = quizPath + "/attempts";
        QuizAttemptDetails first = create(student, attemptsPath, new SubmitQuizAttemptRequest(Map.of(
                relation.id(), relation.options().getFirst().id())), QuizAttemptDetails.class);
        QuizAttemptDetails retry = create(student, attemptsPath, new SubmitQuizAttemptRequest(Map.of(
                relation.id(), relation.options().getLast().id(),
                loading.id(), loading.options().getFirst().id())), QuizAttemptDetails.class);

        assertThat(first.score()).isZero();
        assertThat(retry.score()).isEqualTo(2);
        assertThat(retry.id()).isNotEqualTo(first.id());
        assertThat(body(student.http().getForEntity("/api/me/quiz-attempts", QuizAttemptDetails[].class),
                HttpStatus.OK)).containsExactly(retry, first);
        assertThat(body(teacher.http().getForEntity(attemptsPath, QuizAttemptDetails[].class), HttpStatus.OK))
                .containsExactly(retry, first);
    }

    private CourseScenario createCourseWithEnrolledStudent(String scenario) {
        Long categoryId = categories.save(new Category("ORM " + scenario)).getId();
        AuthenticatedClient teacher = createClient("Мария", scenario + "-teacher@example.org", UserRole.TEACHER);
        AuthenticatedClient student = createClient("Анна", scenario + "-student@example.org", UserRole.STUDENT);
        CourseDetails course = create(teacher, "/api/courses",
                new CreateCourseRequest("Hibernate", "Практика ORM", categoryId), CourseDetails.class);
        CourseModuleDetails module = create(teacher, "/api/courses/" + course.id() + "/modules",
                new CreateModuleRequest("Основы JPA"), CourseModuleDetails.class);
        EnrollmentDetails enrollment = create(student, "/api/courses/" + course.id() + "/enrollment",
                null, EnrollmentDetails.class);
        assertThat(enrollment.status()).isEqualTo(EnrollmentStatus.ACTIVE);
        return new CourseScenario(teacher, student, module.id());
    }

    private AuthenticatedClient createClient(String name, String email, UserRole role) {
        users.save(new User(name, email, role, passwords.encode(PASSWORD)));
        TestRestTemplate client = http.withBasicAuth(email, PASSWORD);
        ResponseEntity<JsonNode> response = client.getForEntity("/api/csrf", JsonNode.class);
        JsonNode csrf = body(response, HttpStatus.OK);
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith("XSRF-TOKEN=");

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie.split(";", 2)[0]);
        headers.set(csrf.get("headerName").asText(), csrf.get("token").asText());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new AuthenticatedClient(client, headers);
    }

    private static <T> T create(AuthenticatedClient client, String path, Object request, Class<T> type) {
        return body(client.http().postForEntity(path, new HttpEntity<>(request, client.writeHeaders()), type),
                HttpStatus.CREATED);
    }

    private static <T> T body(ResponseEntity<T> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        T body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    private record AuthenticatedClient(TestRestTemplate http, HttpHeaders writeHeaders) {
    }

    private record CourseScenario(AuthenticatedClient teacher, AuthenticatedClient student, Long moduleId) {
    }
}
