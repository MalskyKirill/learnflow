package ru.mephi.learnflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
import ru.mephi.learnflow.dto.QuizAttemptDetails;
import ru.mephi.learnflow.dto.QuizOptionDraft;
import ru.mephi.learnflow.dto.QuizQuestionDetails;
import ru.mephi.learnflow.dto.request.QuizQuestionRequest;
import ru.mephi.learnflow.dto.request.QuizQuestionRequest.Option;
import ru.mephi.learnflow.dto.request.QuizRequest;
import ru.mephi.learnflow.dto.request.SubmitQuizAttemptRequest;
import ru.mephi.learnflow.entity.AnswerOption;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.Question;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.mephi.learnflow.controller.ApiTestRequests.basic;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuizControllerTest {

    private static final String PASSWORD = "quiz-api-test-password";
    private static final String MODULE_QUIZ = "/api/modules/{moduleId}/quiz";
    private static final String QUIZ = "/api/quizzes/{quizId}";
    private static final String QUESTIONS = QUIZ + "/questions";
    private static final String QUESTION = QUESTIONS + "/{questionId}";
    private static final String ATTEMPTS = QUIZ + "/attempts";

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

    @Autowired
    private EntityManager entities;

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
                        .content(json.writeValueAsString(new QuizRequest("Связи сущностей"))))
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
        http.perform(get(QUIZ, quizId).headers(studentHeaders))
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
                        .content(json.writeValueAsString(new QuizRequest("Чужой курс"))))
                .andExpect(status().isForbidden());
        Long quizId = quizzes.createQuiz(moduleId, teacher.getId(), "Связи сущностей").id();
        http.perform(post(QUESTIONS, quizId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON).content(questionJson()))
                .andExpect(status().isForbidden());
        http.perform(get(QUIZ, quizId).headers(basic(student.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidQuestionAndDuplicateQuizForModule() throws Exception {
        Long quizId = quizzes.createQuiz(moduleId, teacher.getId(), "Связи сущностей").id();
        RequestPostProcessor credentials = authenticatedWrite(teacher);
        String oneOption = json.writeValueAsString(new QuizQuestionRequest(
                "Какая аннотация нужна?", List.of(new Option("@ManyToOne", true))));
        http.perform(post(QUESTIONS, quizId).with(credentials).contentType(MediaType.APPLICATION_JSON).content(oneOption))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("options"));
        http.perform(post(MODULE_QUIZ, moduleId).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new QuizRequest("Вторая викторина"))))
                .andExpect(status().isConflict());
    }

    @Test
    void storesRepeatAttemptsAndReturnsResultsForStudentAndTeacher() throws Exception {
        enrollments.enrollStudent(courseId, student.getId());
        Long quizId = quizzes.createQuiz(moduleId, teacher.getId(), "Связи сущностей").id();
        QuizQuestionDetails question = addRelationQuestion(quizId);
        Long wrongOption = question.options().getFirst().id();
        Long correctOption = question.options().getLast().id();
        RequestPostProcessor credentials = authenticatedWrite(student);

        MvcResult first = http.perform(post(ATTEMPTS, quizId).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson(question.id(), wrongOption)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.score").value(0)).andReturn();
        MvcResult retry = http.perform(post(ATTEMPTS, quizId).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson(question.id(), correctOption)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.takenAt").isNotEmpty())
                .andExpect(jsonPath("$.answers[0].optionId").value(is(correctOption), Long.class))
                .andReturn();
        assertThat(location(retry)).isNotEqualTo(location(first));

        HttpHeaders learner = basic(student.getEmail(), PASSWORD);
        HttpHeaders instructor = basic(teacher.getEmail(), PASSWORD);
        http.perform(get(location(first)).headers(learner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(0));
        http.perform(get(location(retry)).headers(instructor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1));
        http.perform(get("/api/me/quiz-attempts").headers(learner)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].score").value(1)).andExpect(jsonPath("$[1].score").value(0));
        http.perform(get(ATTEMPTS, quizId).headers(instructor)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].score").value(1)).andExpect(jsonPath("$[1].score").value(0));
    }

    @Test
    void restrictsParticipationAndAccessToAttempts() throws Exception {
        Long quizId = quizzes.createQuiz(moduleId, teacher.getId(), "Связи сущностей").id();
        QuizQuestionDetails question = addRelationQuestion(quizId);
        String answers = attemptJson(question.id(), question.options().getLast().id());
        User peer = createAccount("Иван", "quiz-api-peer@example.org", UserRole.STUDENT);
        User outsider = createAccount("Ирина", "quiz-api-outsider@example.org", UserRole.TEACHER);
        enrollments.enrollStudent(courseId, student.getId());
        MvcResult own = http.perform(post(ATTEMPTS, quizId).with(authenticatedWrite(student))
                        .contentType(MediaType.APPLICATION_JSON).content(answers))
                .andExpect(status().isCreated()).andReturn();

        http.perform(post(ATTEMPTS, quizId).with(authenticatedWrite(peer))
                        .contentType(MediaType.APPLICATION_JSON).content(answers))
                .andExpect(status().isForbidden());
        http.perform(post(ATTEMPTS, quizId).with(authenticatedWrite(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(answers))
                .andExpect(status().isForbidden());
        http.perform(get(location(own)).headers(basic(peer.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
        http.perform(get(ATTEMPTS, quizId).headers(basic(outsider.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
        http.perform(get("/api/me/quiz-attempts").headers(basic(peer.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void invalidAnswersDoNotCreateAttempt() throws Exception {
        enrollments.enrollStudent(courseId, student.getId());
        Long quizId = quizzes.createQuiz(moduleId, teacher.getId(), "Связи сущностей").id();
        QuizQuestionDetails question = addRelationQuestion(quizId);
        RequestPostProcessor credentials = authenticatedWrite(student);

        http.perform(post(ATTEMPTS, quizId).with(credentials).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("answers"));
        http.perform(post(ATTEMPTS, quizId).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson(question.id(), 0L)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        http.perform(post(ATTEMPTS, quizId).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson(question.id(), Long.MAX_VALUE)))
                .andExpect(status().isBadRequest());
        http.perform(get("/api/me/quiz-attempts").headers(basic(student.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void teacherUpdatesAndDeletesQuizAndQuestionsThroughHttp() throws Exception {
        Long quizId = quizzes.createQuiz(moduleId, teacher.getId(), "Связи сущностей").id();
        QuizQuestionDetails question = addRelationQuestion(quizId);
        QuizQuestionDetails remaining = quizzes.addQuestion(quizId, teacher.getId(), "Как отложить загрузку?",
                List.of(new QuizOptionDraft("LAZY", true), new QuizOptionDraft("EAGER", false)));
        RequestPostProcessor credentials = authenticatedWrite(teacher);
        HttpHeaders instructor = basic(teacher.getEmail(), PASSWORD);
        String revision = json.writeValueAsString(new QuizQuestionRequest("Какая связь у урока с модулем?",
                List.of(new Option("@ManyToOne", true), new Option("@OneToOne", false))));

        http.perform(put(QUIZ, quizId).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new QuizRequest("Практика JPA"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Практика JPA"));
        MvcResult updated = http.perform(put(QUESTION, quizId, question.id()).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON).content(revision))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(is(question.id()), Long.class))
                .andExpect(jsonPath("$.text").value("Какая связь у урока с модулем?"))
                .andExpect(jsonPath("$.options[0].text").value("@ManyToOne"))
                .andExpect(jsonPath("$.options[0].correct").value(true))
                .andReturn();
        QuizQuestionDetails revisedQuestion = json.readValue(updated.getResponse().getContentAsByteArray(),
                QuizQuestionDetails.class);
        entities.clear();

        http.perform(get(MODULE_QUIZ, moduleId).headers(instructor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.quiz.title").value("Практика JPA"))
                .andExpect(jsonPath("$.questions[0].text").value(revisedQuestion.text()))
                .andExpect(jsonPath("$.questions[0].options[0].correct").doesNotHaveJsonPath());
        question.options().forEach(option -> assertThat(entities.find(AnswerOption.class, option.id())).isNull());
        MvcResult repeated = http.perform(put(QUESTION, quizId, question.id()).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON).content(revision))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readValue(repeated.getResponse().getContentAsByteArray(), QuizQuestionDetails.class))
                .isEqualTo(revisedQuestion);

        http.perform(delete(QUESTION, quizId, question.id()).with(credentials)).andExpect(status().isNoContent());
        entities.clear();
        http.perform(get(QUESTION, quizId, question.id()).headers(instructor)).andExpect(status().isNotFound());
        revisedQuestion.options().forEach(option -> assertThat(entities.find(AnswerOption.class, option.id())).isNull());
        http.perform(get(QUESTION, quizId, remaining.id()).headers(instructor)).andExpect(status().isOk());

        http.perform(delete(QUIZ, quizId).with(credentials)).andExpect(status().isNoContent());
        entities.clear();
        http.perform(get(MODULE_QUIZ, moduleId).headers(instructor)).andExpect(status().isNotFound());
        assertThat(entities.find(Question.class, remaining.id())).isNull();
        remaining.options().forEach(option -> assertThat(entities.find(AnswerOption.class, option.id())).isNull());
    }

    @Test
    void savedAttemptProtectsQuizAndAnsweredQuestion() throws Exception {
        Long quizId = quizzes.createQuiz(moduleId, teacher.getId(), "Связи сущностей").id();
        QuizQuestionDetails answered = addRelationQuestion(quizId);
        QuizQuestionDetails skipped = addRelationQuestion(quizId);
        Long correctOption = answered.options().getLast().id();
        enrollments.enrollStudent(courseId, student.getId());
        QuizAttemptDetails attempt = quizzes.submitQuiz(quizId, student.getId(), Map.of(answered.id(), correctOption));
        entities.flush();
        entities.clear();
        RequestPostProcessor credentials = authenticatedWrite(teacher);
        String revision = json.writeValueAsString(new QuizQuestionRequest("Как отложить загрузку?",
                List.of(new Option("LAZY", true), new Option("EAGER", false))));

        http.perform(put(QUIZ, quizId).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new QuizRequest("Связи JPA"))))
                .andExpect(status().isOk());
        http.perform(put(QUESTION, quizId, skipped.id()).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(revision))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Как отложить загрузку?"));
        http.perform(delete(QUESTION, quizId, skipped.id()).with(credentials)).andExpect(status().isNoContent());
        http.perform(put(QUESTION, quizId, answered.id()).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson()))
                .andExpect(status().isConflict());
        http.perform(delete(QUESTION, quizId, answered.id()).with(credentials)).andExpect(status().isConflict());
        http.perform(delete(QUIZ, quizId).with(credentials)).andExpect(status().isConflict());

        entities.clear();
        assertThat(quizzes.getQuestionForTeacher(quizId, answered.id(), teacher.getId())).isEqualTo(answered);
        assertThat(quizzes.listStudentAttempts(student.getId())).containsExactly(attempt);
    }

    @Test
    void quizChangesRequireOwnershipAndQuestionMustBelongToQuiz() throws Exception {
        Long quizId = quizzes.createQuiz(moduleId, teacher.getId(), "Связи сущностей").id();
        QuizQuestionDetails question = addRelationQuestion(quizId);
        Long secondModuleId = content.addModule(courseId, teacher.getId(), "Запросы").id();
        Long secondQuizId = quizzes.createQuiz(secondModuleId, teacher.getId(), "JPQL").id();
        User outsider = createAccount("Ирина", "quiz-api-outsider@example.org", UserRole.TEACHER);
        RequestPostProcessor learner = authenticatedWrite(student);
        RequestPostProcessor otherTeacher = authenticatedWrite(outsider);
        RequestPostProcessor instructor = authenticatedWrite(teacher);

        http.perform(put(QUIZ, quizId).with(otherTeacher).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new QuizRequest("Другое название"))))
                .andExpect(status().isForbidden());
        http.perform(delete(QUIZ, quizId).with(learner)).andExpect(status().isForbidden());
        http.perform(put(QUESTION, quizId, question.id()).with(learner).contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson()))
                .andExpect(status().isForbidden());
        http.perform(delete(QUESTION, quizId, question.id()).with(otherTeacher)).andExpect(status().isForbidden());
        http.perform(delete(QUESTION, secondQuizId, question.id()).with(instructor))
                .andExpect(status().isNotFound());
        http.perform(put(QUESTION, secondQuizId, question.id()).with(instructor).contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson()))
                .andExpect(status().isNotFound());

        assertThat(quizzes.getQuestionForTeacher(quizId, question.id(), teacher.getId())).isEqualTo(question);
    }

    private QuizQuestionDetails addRelationQuestion(Long quizId) {
        return quizzes.addQuestion(quizId, teacher.getId(), "Какая аннотация нужна?",
                List.of(new QuizOptionDraft("@OneToMany", false), new QuizOptionDraft("@ManyToOne", true)));
    }

    private String attemptJson(Long questionId, Long optionId) throws Exception {
        return json.writeValueAsString(new SubmitQuizAttemptRequest(Map.of(questionId, optionId)));
    }

    private String questionJson() throws Exception {
        return json.writeValueAsString(new QuizQuestionRequest("Какая аннотация нужна?",
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
