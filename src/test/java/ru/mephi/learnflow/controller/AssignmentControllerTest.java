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
import ru.mephi.learnflow.dto.AssignmentDetails;
import ru.mephi.learnflow.dto.request.CreateAssignmentRequest;
import ru.mephi.learnflow.dto.request.UpdateAssignmentRequest;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.UserRepository;
import ru.mephi.learnflow.service.AssignmentService;
import ru.mephi.learnflow.service.CourseContentService;
import ru.mephi.learnflow.service.CourseService;
import ru.mephi.learnflow.service.EnrollmentService;
import ru.mephi.learnflow.service.SubmissionService;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.mephi.learnflow.controller.ApiTestRequests.basic;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssignmentControllerTest {

    private static final String PASSWORD = "assignment-api-test-password";
    private static final String LESSON_ASSIGNMENTS = "/api/lessons/{lessonId}/assignments";
    private static final String ASSIGNMENT = "/api/assignments/{assignmentId}";

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
    private SubmissionService submissions;

    @Autowired
    private EntityManager entities;

    private MockMvc http;
    private User teacher;
    private Long courseId;
    private Long lessonId;

    @BeforeEach
    void prepareLessonAndHttpClient() {
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilters).build();
        teacher = createAccount("Мария", "assignment-teacher@example.org", UserRole.TEACHER);
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        courseId = courses.createCourse("Hibernate", null, categoryId, teacher.getId()).id();
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

    @Test
    void rejectsFractionalMaximumWhenCreatingOrUpdatingAssignment() throws Exception {
        AssignmentDetails assignment = assignments.createAssignment(lessonId, teacher.getId(), "Связи", null, null, 20);
        RequestPostProcessor writer = authenticatedWrite(teacher);
        String request = """
                {"title":"Практика JPA","maxScore":7.5}
                """;

        http.perform(post(LESSON_ASSIGNMENTS, lessonId).with(writer)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("maxScore"))
                .andExpect(jsonPath("$.errors[0].message").value("Укажите целое число"));
        http.perform(put(ASSIGNMENT, assignment.id()).with(writer)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("maxScore"));

        assertThat(assignments.listAssignments(lessonId)).containsExactly(assignment);
    }

    @Test
    void teacherUpdatesAndDeletesAssignment() throws Exception {
        Long assignmentId = assignments.createAssignment(lessonId, teacher.getId(), "Связи", null, null, 20).id();
        RequestPostProcessor writer = authenticatedWrite(teacher);
        HttpHeaders reader = basic(teacher.getEmail(), PASSWORD);
        Instant dueAt = Instant.parse("2027-01-01T12:00:00Z");
        UpdateAssignmentRequest update = new UpdateAssignmentRequest("Связи JPA", "Добавь пример", dueAt, 30);

        http.perform(put(ASSIGNMENT, assignmentId).with(writer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(update)))
                .andExpect(status().isOk());
        entities.flush();
        entities.clear();

        http.perform(get(ASSIGNMENT, assignmentId).headers(reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(update.title()))
                .andExpect(jsonPath("$.description").value(update.description()))
                .andExpect(jsonPath("$.dueAt").value(dueAt.toString()))
                .andExpect(jsonPath("$.maxScore").value(30));

        http.perform(delete(ASSIGNMENT, assignmentId).with(writer)).andExpect(status().isNoContent());
        entities.clear();
        http.perform(get(ASSIGNMENT, assignmentId).headers(reader)).andExpect(status().isNotFound());
        http.perform(get(LESSON_ASSIGNMENTS, lessonId).headers(reader))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void onlyCourseTeacherCanUpdateAndDeleteAssignment() throws Exception {
        Long assignmentId = assignments.createAssignment(lessonId, teacher.getId(), "Связи", null, null, 20).id();
        User outsider = createAccount("Ирина", "assignment-outsider@example.org", UserRole.TEACHER);
        User student = createAccount("Анна", "assignment-student@example.org", UserRole.STUDENT);
        String request = json.writeValueAsString(new UpdateAssignmentRequest("JPQL", null, null, 20));

        http.perform(put(ASSIGNMENT, assignmentId).with(authenticatedWrite(outsider))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        http.perform(delete(ASSIGNMENT, assignmentId).with(authenticatedWrite(student)))
                .andExpect(status().isForbidden());
        assertThat(assignments.getAssignment(assignmentId).title()).isEqualTo("Связи");
    }

    @Test
    void preservesSubmissionsAndRejectsMaximumBelowAnExistingGrade() throws Exception {
        Long assignmentId = assignments.createAssignment(lessonId, teacher.getId(), "Связи", null, null, 20).id();
        User student = createAccount("Анна", "assignment-student@example.org", UserRole.STUDENT);
        enrollments.enrollStudent(courseId, student.getId());
        Long submissionId = submissions.submitAssignment(assignmentId, student.getId(), "Связь ManyToOne").id();
        RequestPostProcessor writer = authenticatedWrite(teacher);

        http.perform(delete(ASSIGNMENT, assignmentId).with(writer)).andExpect(status().isConflict());
        submissions.gradeSubmission(submissionId, teacher.getId(), 18, "Укажи внешний ключ");
        entities.flush();
        entities.clear();

        http.perform(put(ASSIGNMENT, assignmentId).with(writer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new UpdateAssignmentRequest("Связи JPA", null, null, 18))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maxScore").value(18));
        http.perform(put(ASSIGNMENT, assignmentId).with(writer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new UpdateAssignmentRequest("Связи JPA", null, null, 17))))
                .andExpect(status().isConflict());
        entities.flush();
        entities.clear();

        assertThat(assignments.getAssignment(assignmentId).maxScore()).isEqualTo(18);
        assertThat(submissions.getSubmission(submissionId, student.getId()).score()).isEqualTo(18);
    }

    private User createAccount(String name, String email, UserRole role) {
        return users.save(new User(name, email, role, passwords.encode(PASSWORD)));
    }

    private RequestPostProcessor authenticatedWrite(User user) throws Exception {
        return ApiTestRequests.authenticatedWrite(http, json, user.getEmail(), PASSWORD);
    }
}
