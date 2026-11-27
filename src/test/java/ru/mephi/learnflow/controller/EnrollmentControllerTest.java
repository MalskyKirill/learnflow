package ru.mephi.learnflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.UserRepository;
import ru.mephi.learnflow.service.CourseService;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.mephi.learnflow.controller.ApiTestRequests.basic;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnrollmentControllerTest {

    private static final String PASSWORD = "enrollment-api-test-password";
    private static final String ENROLLMENT_PATH = "/api/courses/{courseId}/enrollment";
    private static final String STUDENTS_PATH = "/api/courses/{courseId}/students";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilters;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private CourseService courses;

    @Autowired
    private UserRepository users;

    private MockMvc http;
    private User teacher;
    private User student;
    private Long courseId;

    @BeforeEach
    void prepareCourseAndAccounts() {
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilters).build();
        teacher = createAccount("Мария", "enrollment-owner@example.org", UserRole.TEACHER);
        student = createAccount("Анна", "enrollment-student@example.org", UserRole.STUDENT);
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        courseId = courses.createCourse("Hibernate", null, categoryId, teacher.getId()).id();
    }

    @Test
    void studentCanEnrollViewAndLeaveCourse() throws Exception {
        RequestPostProcessor credentials = authenticatedWrite(student);
        HttpHeaders studentHeaders = basic(student.getEmail(), PASSWORD);
        HttpHeaders teacherHeaders = basic(teacher.getEmail(), PASSWORD);

        http.perform(post(ENROLLMENT_PATH, courseId).with(credentials))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/courses/" + courseId + "/enrollment")))
                .andExpect(jsonPath("$.studentId").value(is(student.getId()), Long.class))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        http.perform(get(ENROLLMENT_PATH, courseId).headers(studentHeaders))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        http.perform(get("/api/me/courses").headers(studentHeaders))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Hibernate"));
        http.perform(get(STUDENTS_PATH, courseId).headers(teacherHeaders))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Анна"));

        http.perform(delete(ENROLLMENT_PATH, courseId).with(credentials)).andExpect(status().isNoContent());
        http.perform(delete(ENROLLMENT_PATH, courseId).with(credentials)).andExpect(status().isNoContent());
        http.perform(get(ENROLLMENT_PATH, courseId).headers(studentHeaders)).andExpect(status().isNotFound());
        http.perform(get("/api/me/courses").headers(studentHeaders))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        http.perform(get(STUDENTS_PATH, courseId).headers(teacherHeaders))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void duplicateEnrollmentReturnsConflict() throws Exception {
        RequestPostProcessor credentials = authenticatedWrite(student);

        http.perform(post(ENROLLMENT_PATH, courseId).with(credentials)).andExpect(status().isCreated());
        http.perform(post(ENROLLMENT_PATH, courseId).with(credentials))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void restrictsEnrollmentAndCourseRosterToTheirRoles() throws Exception {
        User outsider = createAccount("Ирина", "enrollment-outsider@example.org", UserRole.TEACHER);

        http.perform(post(ENROLLMENT_PATH, courseId).with(authenticatedWrite(teacher)))
                .andExpect(status().isForbidden());
        http.perform(get(STUDENTS_PATH, courseId).headers(basic(outsider.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
        http.perform(get(STUDENTS_PATH, courseId).headers(basic(student.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    private User createAccount(String name, String email, UserRole role) {
        return users.save(new User(name, email, role, passwords.encode(PASSWORD)));
    }

    private RequestPostProcessor authenticatedWrite(User user) throws Exception {
        return ApiTestRequests.authenticatedWrite(http, json, user.getEmail(), PASSWORD);
    }
}
