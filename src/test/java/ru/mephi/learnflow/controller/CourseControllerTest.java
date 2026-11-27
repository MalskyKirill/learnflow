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
import ru.mephi.learnflow.dto.CourseDetails;
import ru.mephi.learnflow.dto.request.CreateCourseRequest;
import ru.mephi.learnflow.dto.request.UpdateCourseRequest;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.UserRepository;
import ru.mephi.learnflow.service.CourseService;
import ru.mephi.learnflow.service.EnrollmentService;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseControllerTest {

    private static final String PASSWORD = "course-api-test-password";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CourseService courses;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entities;

    @Autowired
    private FilterChainProxy securityFilters;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private EnrollmentService enrollments;

    private MockMvc http;

    @BeforeEach
    void prepareHttpClient() {
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilters).build();
    }

    @Test
    void returnsCoursesFromPostgresAsJson() throws Exception {
        Category category = categories.save(new Category("Базы данных"));
        User teacher = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER));
        CourseDetails course = courses.createCourse("Hibernate", "Введение в ORM", category.getId(), teacher.getId());
        entities.flush();
        entities.clear();

        http.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(is(course.id()), Long.class))
                .andExpect(jsonPath("$[0].title").value("Hibernate"))
                .andExpect(jsonPath("$[0].description").value("Введение в ORM"))
                .andExpect(jsonPath("$[0].categoryId").value(is(category.getId()), Long.class))
                .andExpect(jsonPath("$[0].teacherId").value(is(teacher.getId()), Long.class));
    }

    @Test
    void teacherCreatesUpdatesAndDeletesCourseThroughHttp() throws Exception {
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        User teacher = createAccount("Мария", "course-owner@example.org", UserRole.TEACHER);
        RequestPostProcessor credentials = authenticatedWrite(teacher);
        MvcResult created = http.perform(post("/api/courses").with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateCourseRequest("Hibernate", "JPA", categoryId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherId").value(is(teacher.getId()), Long.class))
                .andReturn();
        String location = created.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        URI courseUri = URI.create(location);
        assertThat(courseUri.getPath()).matches("/api/courses/[0-9]+");

        http.perform(get(courseUri)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Hibernate"));
        http.perform(put(courseUri).with(credentials).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new UpdateCourseRequest("ORM", "Связи", categoryId, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("ORM"))
                .andExpect(jsonPath("$.teacherId").value(is(teacher.getId()), Long.class));

        http.perform(delete(courseUri).with(credentials)).andExpect(status().isNoContent());
        http.perform(get(courseUri)).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsCourseWritesWithoutTheRequiredRoleOrOwnership() throws Exception {
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        User owner = createAccount("Мария", "course-owner@example.org", UserRole.TEACHER);
        User otherTeacher = createAccount("Ирина", "course-teacher@example.org", UserRole.TEACHER);
        User student = createAccount("Анна", "course-student@example.org", UserRole.STUDENT);
        CourseDetails course = courses.createCourse("Hibernate", null, categoryId, owner.getId());
        RequestPostProcessor outsider = authenticatedWrite(otherTeacher);
        RequestPostProcessor learner = authenticatedWrite(student);

        http.perform(post("/api/courses").with(learner).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateCourseRequest("SQL", null, categoryId))))
                .andExpect(status().isForbidden());
        http.perform(put("/api/courses/{id}", course.id()).with(outsider).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new UpdateCourseRequest("SQL", null, categoryId, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        http.perform(delete("/api/courses/{id}", course.id()).with(outsider))
                .andExpect(status().isForbidden());
        assertThat(courses.listCourses()).containsExactly(course);
    }

    @Test
    void reportsInvalidBodyAndPathParameter() throws Exception {
        User teacher = createAccount("Мария", "course-owner@example.org", UserRole.TEACHER);
        String invalid = json.writeValueAsString(new CreateCourseRequest(" ", null, 1L));

        http.perform(post("/api/courses").with(authenticatedWrite(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        http.perform(get("/api/courses/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("courseId"));
    }

    @Test
    void reportsConflictWhenDeletingCourseWithEnrollment() throws Exception {
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        User teacher = createAccount("Мария", "course-owner@example.org", UserRole.TEACHER);
        User student = users.save(new User("Анна", "course-student@example.org", UserRole.STUDENT));
        CourseDetails course = courses.createCourse("Hibernate", null, categoryId, teacher.getId());
        enrollments.enrollStudent(course.id(), student.getId());

        http.perform(delete("/api/courses/{id}", course.id()).with(authenticatedWrite(teacher)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertThat(courses.getCourse(course.id())).isEqualTo(course);
    }

    private User createAccount(String name, String email, UserRole role) {
        return users.save(new User(name, email, role, passwords.encode(PASSWORD)));
    }

    private RequestPostProcessor authenticatedWrite(User user) throws Exception {
        return ApiTestRequests.authenticatedWrite(http, json, user.getEmail(), PASSWORD);
    }
}
