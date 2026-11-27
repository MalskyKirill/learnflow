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
import ru.mephi.learnflow.dto.request.CreateAssignmentRequest;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.UserRepository;
import ru.mephi.learnflow.service.CourseContentService;
import ru.mephi.learnflow.service.CourseService;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.mephi.learnflow.controller.ApiTestRequests.basic;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssignmentControllerTest {

    private static final String PASSWORD = "assignment-api-test-password";
    private static final String LESSON_ASSIGNMENTS = "/api/lessons/{lessonId}/assignments";

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
    private Long lessonId;

    @BeforeEach
    void prepareLessonAndHttpClient() {
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilters).build();
        teacher = createAccount("Мария", "assignment-teacher@example.org", UserRole.TEACHER);
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        Long courseId = courses.createCourse("Hibernate", null, categoryId, teacher.getId()).id();
        Long moduleId = content.addModule(courseId, teacher.getId(), "Основы").id();
        lessonId = content.addLesson(moduleId, teacher.getId(), "Сущности и связи", null).id();
    }

    @Test
    void teacherCreatesAssignmentsAndStudentReadsThem() throws Exception {
        RequestPostProcessor credentials = authenticatedWrite(teacher);
        Instant deadline = Instant.parse("2027-01-01T12:00:00Z");
        MvcResult created = http.perform(post(LESSON_ASSIGNMENTS, lessonId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAssignmentRequest(
                                "Связи JPA", "Опиши связи", deadline, 20))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueAt").value(deadline.toString()))
                .andExpect(jsonPath("$.maxScore").value(20))
                .andReturn();
        http.perform(post(LESSON_ASSIGNMENTS, lessonId).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAssignmentRequest("JPQL", null, null, 10))))
                .andExpect(status().isCreated());

        String location = created.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        URI assignmentUri = URI.create(location);
        assertThat(assignmentUri.getPath()).matches("/api/assignments/[0-9]+");
        User student = createAccount("Анна", "assignment-student@example.org", UserRole.STUDENT);
        HttpHeaders reader = basic(student.getEmail(), PASSWORD);
        http.perform(get(assignmentUri).headers(reader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Опиши связи"));
        http.perform(get(LESSON_ASSIGNMENTS, lessonId).headers(reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Связи JPA"))
                .andExpect(jsonPath("$[1].title").value("JPQL"));
    }

    @Test
    void onlyAssignedTeacherCanCreateAssignments() throws Exception {
        User outsider = createAccount("Ирина", "assignment-outsider@example.org", UserRole.TEACHER);
        User student = createAccount("Анна", "assignment-student@example.org", UserRole.STUDENT);
        String request = json.writeValueAsString(new CreateAssignmentRequest("Связи", null, null, 20));

        http.perform(post(LESSON_ASSIGNMENTS, lessonId).with(authenticatedWrite(outsider))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        http.perform(post(LESSON_ASSIGNMENTS, lessonId).with(authenticatedWrite(student))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        http.perform(get(LESSON_ASSIGNMENTS, lessonId).headers(basic(teacher.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsInvalidScoreAndReportsMissingLesson() throws Exception {
        http.perform(post(LESSON_ASSIGNMENTS, lessonId).with(authenticatedWrite(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAssignmentRequest("Связи", null, null, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("maxScore"));
        http.perform(get(LESSON_ASSIGNMENTS, Long.MAX_VALUE).headers(basic(teacher.getEmail(), PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private User createAccount(String name, String email, UserRole role) {
        return users.save(new User(name, email, role, passwords.encode(PASSWORD)));
    }

    private RequestPostProcessor authenticatedWrite(User user) throws Exception {
        return ApiTestRequests.authenticatedWrite(http, json, user.getEmail(), PASSWORD);
    }
}
