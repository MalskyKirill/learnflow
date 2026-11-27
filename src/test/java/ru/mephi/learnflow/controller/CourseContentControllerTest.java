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
import ru.mephi.learnflow.dto.request.CreateLessonRequest;
import ru.mephi.learnflow.dto.request.CreateModuleRequest;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.UserRepository;
import ru.mephi.learnflow.service.CourseContentService;
import ru.mephi.learnflow.service.CourseService;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
