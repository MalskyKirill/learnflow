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
import ru.mephi.learnflow.dto.request.CreateLessonRequest;
import ru.mephi.learnflow.dto.request.CreateModuleRequest;
import ru.mephi.learnflow.dto.request.UpdateLessonRequest;
import ru.mephi.learnflow.dto.request.UpdateModuleRequest;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.UserRepository;
import ru.mephi.learnflow.service.AssignmentService;
import ru.mephi.learnflow.service.CourseContentService;
import ru.mephi.learnflow.service.CourseService;
import ru.mephi.learnflow.service.EnrollmentService;
import ru.mephi.learnflow.service.QuizService;
import ru.mephi.learnflow.service.SubmissionService;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
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
class CourseContentControllerTest {

    private static final String PASSWORD = "course-content-test-password";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilters;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CourseService courses;

    @Autowired
    private CourseContentService content;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private AssignmentService assignments;

    @Autowired
    private EnrollmentService enrollments;

    @Autowired
    private QuizService quizzes;

    @Autowired
    private SubmissionService submissions;

    @Autowired
    private EntityManager entities;

    private MockMvc http;
    private User teacher;
    private Long courseId;

    @BeforeEach
    void prepareCourseAndHttpClient() {
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilters).build();
        teacher = createAccount("Мария", "course-content-owner@example.org", UserRole.TEACHER);
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        courseId = courses.createCourse("Hibernate", null, categoryId, teacher.getId()).id();
    }

    @Test
    void createsModulesAndLessonsAndReadsProgramInOrder() throws Exception {
        RequestPostProcessor credentials = authenticatedWrite(teacher);
        MvcResult first = http.perform(post("/api/courses/{id}/modules", courseId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateModuleRequest("Основы"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1))
                .andReturn();
        long moduleId = json.readTree(first.getResponse().getContentAsString()).get("id").asLong();
        http.perform(post("/api/courses/{id}/modules", courseId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateModuleRequest("Запросы"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(2));

        MvcResult lesson = http.perform(post("/api/modules/{id}/lessons", moduleId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateLessonRequest("Сущности", "Разметка JPA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1))
                .andReturn();
        http.perform(post("/api/modules/{id}/lessons", moduleId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateLessonRequest("Связи", "Внешние ключи"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(2));

        HttpHeaders headers = basic(teacher.getEmail(), PASSWORD);
        http.perform(get(location(first)).headers(headers))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Основы"));
        http.perform(get(location(lesson)).headers(headers))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Разметка JPA"));
        http.perform(get("/api/courses/{id}/modules", courseId).headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Основы"))
                .andExpect(jsonPath("$[1].title").value("Запросы"));
        http.perform(get("/api/courses/{id}/program", courseId).headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.course.title").value("Hibernate"))
                .andExpect(jsonPath("$.modules[0].lessons[0].title").value("Сущности"))
                .andExpect(jsonPath("$.modules[0].lessons[1].title").value("Связи"))
                .andExpect(jsonPath("$.modules[1].details.title").value("Запросы"))
                .andExpect(jsonPath("$.modules[1].lessons").isEmpty());
    }

    @Test
    void onlyCourseTeacherCanAddModuleOrLesson() throws Exception {
        Long moduleId = content.addModule(courseId, teacher.getId(), "Основы").id();
        User student = createAccount("Анна", "course-content-student@example.org", UserRole.STUDENT);
        User outsider = createAccount("Ирина", "course-content-teacher@example.org", UserRole.TEACHER);

        http.perform(post("/api/courses/{id}/modules", courseId).with(authenticatedWrite(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateModuleRequest("Запросы"))))
                .andExpect(status().isForbidden());
        http.perform(post("/api/modules/{id}/lessons", moduleId).with(authenticatedWrite(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateLessonRequest("Сущности", null))))
                .andExpect(status().isForbidden());
        http.perform(get("/api/courses/{id}/program", courseId)).andExpect(status().isUnauthorized());
        http.perform(get("/api/courses/{id}/program", courseId).headers(basic(student.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(1))
                .andExpect(jsonPath("$.modules[0].lessons").isEmpty());
    }

    @Test
    void reportsInvalidModuleTitleAndMissingModule() throws Exception {
        RequestPostProcessor credentials = authenticatedWrite(teacher);
        http.perform(post("/api/courses/{id}/modules", courseId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateModuleRequest(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        http.perform(post("/api/modules/{id}/lessons", Long.MAX_VALUE).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateLessonRequest("Сущности", null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void teacherUpdatesAndDeletesModulesAndLessonsThroughHttp() throws Exception {
        Long moduleId = content.addModule(courseId, teacher.getId(), "Основы").id();
        Long otherModuleId = content.addModule(courseId, teacher.getId(), "Запросы").id();
        Long lessonId = content.addLesson(moduleId, teacher.getId(), "Сущности", "Разметка JPA").id();
        Long otherLessonId = content.addLesson(moduleId, teacher.getId(), "Коллекции", null).id();
        Long assignmentId = assignments.createAssignment(lessonId, teacher.getId(), "Связи", null, null, 10).id();
        Long otherAssignmentId = assignments.createAssignment(otherLessonId, teacher.getId(),
                "Типы коллекций", null, null, 10).id();
        RequestPostProcessor credentials = authenticatedWrite(teacher);
        HttpHeaders headers = basic(teacher.getEmail(), PASSWORD);

        http.perform(put("/api/modules/{id}", moduleId).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new UpdateModuleRequest("Основы JPA"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.position").value(1));
        http.perform(put("/api/lessons/{id}", lessonId).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new UpdateLessonRequest("Связи", "Внешние ключи"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.position").value(1));
        entities.flush();
        entities.clear();

        http.perform(get("/api/modules/{id}", moduleId).headers(headers))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Основы JPA"));
        http.perform(get("/api/lessons/{id}", lessonId).headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Связи"))
                .andExpect(jsonPath("$.content").value("Внешние ключи"));

        http.perform(delete("/api/lessons/{id}", lessonId).with(credentials)).andExpect(status().isNoContent());
        entities.clear();
        http.perform(get("/api/lessons/{id}", lessonId).headers(headers)).andExpect(status().isNotFound());
        http.perform(get("/api/assignments/{id}", assignmentId).headers(headers)).andExpect(status().isNotFound());
        http.perform(get("/api/lessons/{id}", otherLessonId).headers(headers))
                .andExpect(status().isOk()).andExpect(jsonPath("$.position").value(2));

        http.perform(delete("/api/modules/{id}", moduleId).with(credentials)).andExpect(status().isNoContent());
        entities.clear();
        http.perform(get("/api/modules/{id}", moduleId).headers(headers)).andExpect(status().isNotFound());
        http.perform(get("/api/lessons/{id}", otherLessonId).headers(headers)).andExpect(status().isNotFound());
        http.perform(get("/api/assignments/{id}", otherAssignmentId).headers(headers)).andExpect(status().isNotFound());
        http.perform(get("/api/modules/{id}", otherModuleId).headers(headers))
                .andExpect(status().isOk()).andExpect(jsonPath("$.position").value(2));
    }

    @Test
    void onlyCourseTeacherCanUpdateAndDeleteModulesAndLessons() throws Exception {
        Long moduleId = content.addModule(courseId, teacher.getId(), "Основы").id();
        Long lessonId = content.addLesson(moduleId, teacher.getId(), "Сущности", null).id();
        User student = createAccount("Анна", "course-content-student@example.org", UserRole.STUDENT);
        User outsider = createAccount("Ирина", "course-content-teacher@example.org", UserRole.TEACHER);
        RequestPostProcessor studentCredentials = authenticatedWrite(student);
        RequestPostProcessor otherTeacherCredentials = authenticatedWrite(outsider);

        http.perform(put("/api/modules/{id}", moduleId).with(studentCredentials).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new UpdateModuleRequest("Другое название"))))
                .andExpect(status().isForbidden());
        http.perform(put("/api/lessons/{id}", lessonId).with(otherTeacherCredentials)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new UpdateLessonRequest("Другой урок", null))))
                .andExpect(status().isForbidden());
        http.perform(delete("/api/modules/{id}", moduleId).with(otherTeacherCredentials))
                .andExpect(status().isForbidden());
        http.perform(delete("/api/lessons/{id}", lessonId).with(studentCredentials))
                .andExpect(status().isForbidden());
        assertThat(content.getModule(moduleId).title()).isEqualTo("Основы");
        assertThat(content.getLesson(lessonId).title()).isEqualTo("Сущности");
    }

    @Test
    void deletionPreservesQuizzesAndStudentSubmissions() throws Exception {
        Long moduleId = content.addModule(courseId, teacher.getId(), "Основы").id();
        Long lessonId = content.addLesson(moduleId, teacher.getId(), "Сущности", null).id();
        Long assignmentId = assignments.createAssignment(lessonId, teacher.getId(), "Связи", null, null, 10).id();
        User student = createAccount("Анна", "course-content-student@example.org", UserRole.STUDENT);
        enrollments.enrollStudent(courseId, student.getId());
        Long submissionId = submissions.submitAssignment(assignmentId, student.getId(), "Связь ManyToOne").id();

        Long quizModuleId = content.addModule(courseId, teacher.getId(), "Запросы").id();
        Long quizId = quizzes.createQuiz(quizModuleId, teacher.getId(), "JPQL").id();
        entities.flush();
        entities.clear();
        RequestPostProcessor credentials = authenticatedWrite(teacher);

        http.perform(delete("/api/lessons/{id}", lessonId).with(credentials)).andExpect(status().isConflict());
        http.perform(delete("/api/modules/{id}", moduleId).with(credentials)).andExpect(status().isConflict());
        http.perform(delete("/api/modules/{id}", quizModuleId).with(credentials)).andExpect(status().isConflict());
        assertThat(submissions.getSubmission(submissionId, student.getId()).assignmentId()).isEqualTo(assignmentId);
        assertThat(quizzes.getQuiz(quizId, teacher.getId()).quiz().moduleId()).isEqualTo(quizModuleId);
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
