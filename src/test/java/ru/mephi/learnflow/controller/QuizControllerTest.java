package ru.mephi.learnflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.mephi.learnflow.dto.request.CreateQuizQuestionRequest;
import ru.mephi.learnflow.dto.request.CreateQuizQuestionRequest.Option;
import ru.mephi.learnflow.dto.request.CreateQuizRequest;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.UserRepository;
import ru.mephi.learnflow.service.CourseContentService;
import ru.mephi.learnflow.service.CourseService;
import ru.mephi.learnflow.service.EnrollmentService;
import ru.mephi.learnflow.service.QuizService;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.mephi.learnflow.controller.ApiTestRequests.basic;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuizControllerTest {

    private static final String PASSWORD = "quiz-api-test-password";
    private static final String MODULE_QUIZ = "/api/modules/{moduleId}/quiz";
    private static final String QUESTIONS = "/api/quizzes/{quizId}/questions";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilters;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private CourseService courses;

    @Autowired
    private CourseContentService content;

    @Autowired
    private EnrollmentService enrollments;

    @Autowired
    private QuizService quizzes;

    private MockMvc http;
    private User teacher;
    private User student;
    private Long courseId;
    private Long moduleId;

    @BeforeEach
    void prepareModuleAndAccounts() {
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilters).build();
        teacher = createAccount("Мария", "quiz-api-teacher@example.org", UserRole.TEACHER);
        student = createAccount("Анна", "quiz-api-student@example.org", UserRole.STUDENT);
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        courseId = courses.createCourse("Hibernate", null, categoryId, teacher.getId()).id();
        moduleId = content.addModule(courseId, teacher.getId(), "Основы").id();
    }

    @Test
    void teacherCreatesQuizAndStudentReadsOptionsWithoutAnswerKey() throws Exception {
        enrollments.enrollStudent(courseId, student.getId());
        RequestPostProcessor credentials = authenticatedWrite(teacher);
        MvcResult created = http.perform(post(MODULE_QUIZ, moduleId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateQuizRequest("Связи сущностей"))))
                .andExpect(status().isCreated()).andReturn();
        long quizId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        MvcResult question = http.perform(post(QUESTIONS, quizId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON).content(questionJson()))
                .andExpect(status().isCreated()).andReturn();
        URI questionUri = location(question);
        HttpHeaders teacherHeaders = basic(teacher.getEmail(), PASSWORD);
        http.perform(get(questionUri).headers(teacherHeaders))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[1].correct").value(true));

        HttpHeaders studentHeaders = basic(student.getEmail(), PASSWORD);
        http.perform(get(location(created)).headers(studentHeaders))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quiz.title").value("Связи сущностей"))
                .andExpect(jsonPath("$.questions[0].options[1].text").value("@ManyToOne"))
                .andExpect(jsonPath("$.questions[0].options[1].correct").doesNotHaveJsonPath());
        http.perform(get("/api/quizzes/{quizId}", quizId).headers(studentHeaders))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].text").value("Какая аннотация нужна?"))
                .andExpect(jsonPath("$.questions[0].options[0].correct").doesNotHaveJsonPath());
        http.perform(get(questionUri).headers(studentHeaders)).andExpect(status().isForbidden());
    }

    @Test
    void rejectsAnotherTeacherAndStudentWithoutEnrollment() throws Exception {
        User outsider = createAccount("Ирина", "quiz-api-outsider@example.org", UserRole.TEACHER);
        RequestPostProcessor credentials = authenticatedWrite(outsider);
        http.perform(post(MODULE_QUIZ, moduleId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateQuizRequest("Чужой курс"))))
                .andExpect(status().isForbidden());
        Long quizId = quizzes.createQuiz(moduleId, teacher.getId(), "Связи сущностей").id();
        http.perform(post(QUESTIONS, quizId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON).content(questionJson()))
                .andExpect(status().isForbidden());
        http.perform(get("/api/quizzes/{quizId}", quizId).headers(basic(student.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidQuestionAndDuplicateQuizForModule() throws Exception {
        Long quizId = quizzes.createQuiz(moduleId, teacher.getId(), "Связи сущностей").id();
        RequestPostProcessor credentials = authenticatedWrite(teacher);
        String oneOption = json.writeValueAsString(new CreateQuizQuestionRequest(
                "Какая аннотация нужна?", List.of(new Option("@ManyToOne", true))));
        http.perform(post(QUESTIONS, quizId).with(credentials).contentType(MediaType.APPLICATION_JSON).content(oneOption))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("options"));
        http.perform(post(MODULE_QUIZ, moduleId).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateQuizRequest("Вторая викторина"))))
                .andExpect(status().isConflict());
    }

    private String questionJson() throws Exception {
        return json.writeValueAsString(new CreateQuizQuestionRequest("Какая аннотация нужна?",
                List.of(new Option("@OneToMany", false), new Option("@ManyToOne", true))));
    }

    private User createAccount(String name, String email, UserRole role) {
        return users.save(new User(name, email, role, passwords.encode(PASSWORD)));
    }

    private RequestPostProcessor authenticatedWrite(User user) throws Exception {
        return ApiTestRequests.authenticatedWrite(http, json, user.getEmail(), PASSWORD);
    }

    private static URI location(MvcResult created) {
        String location = created.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        return URI.create(location);
    }
}
