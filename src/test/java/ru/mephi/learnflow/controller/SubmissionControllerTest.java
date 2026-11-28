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
import ru.mephi.learnflow.dto.SubmissionDetails;
import ru.mephi.learnflow.dto.request.CreateSubmissionRequest;
import ru.mephi.learnflow.dto.request.GradeSubmissionRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.mephi.learnflow.controller.ApiTestRequests.basic;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubmissionControllerTest {

    private static final String PASSWORD = "submission-api-test-password";
    private static final String ASSIGNMENT_SUBMISSIONS = "/api/assignments/{assignmentId}/submissions";
    private static final String SUBMISSION_GRADE = "/api/submissions/{submissionId}/grade";

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
    private AssignmentService assignments;

    @Autowired
    private EnrollmentService enrollments;

    @Autowired
    private SubmissionService submissions;

    private MockMvc http;
    private User teacher;
    private User student;
    private Long courseId;
    private Long assignmentId;

    @BeforeEach
    void prepareAssignmentAndAccounts() {
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilters).build();
        teacher = createAccount("Мария", "submissions-teacher@example.org", UserRole.TEACHER);
        student = createAccount("Анна", "submissions-student@example.org", UserRole.STUDENT);
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        courseId = courses.createCourse("Hibernate", null, categoryId, teacher.getId()).id();
        Long moduleId = content.addModule(courseId, teacher.getId(), "Основы").id();
        Long lessonId = content.addLesson(moduleId, teacher.getId(), "Связи", null).id();
        assignmentId = assignments.createAssignment(lessonId, teacher.getId(), "JPA-связи", null, null, 20).id();
    }

    @Test
    void studentSubmitsAndTeacherGradesThroughHttp() throws Exception {
        enrollments.enrollStudent(courseId, student.getId());
        MvcResult created = http.perform(post(ASSIGNMENT_SUBMISSIONS, assignmentId).with(authenticatedWrite(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateSubmissionRequest("Связь многие к одному"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(is(student.getId()), Long.class))
                .andExpect(jsonPath("$.score").value(nullValue()))
                .andReturn();
        String location = created.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        URI submissionUri = URI.create(location);
        assertThat(submissionUri.getPath()).matches("/api/submissions/[0-9]+");
        long submissionId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        HttpHeaders studentHeaders = basic(student.getEmail(), PASSWORD);
        HttpHeaders teacherHeaders = basic(teacher.getEmail(), PASSWORD);

        http.perform(get(submissionUri).headers(studentHeaders)).andExpect(status().isOk());
        http.perform(get(ASSIGNMENT_SUBMISSIONS, assignmentId).headers(teacherHeaders))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(is(submissionId), Long.class));
        http.perform(put(SUBMISSION_GRADE, submissionId).with(authenticatedWrite(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new GradeSubmissionRequest(18, "Добавь пример"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(18));
        http.perform(get("/api/me/submissions").headers(studentHeaders))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].score").value(18))
                .andExpect(jsonPath("$[0].feedback").value("Добавь пример"));
    }

    @Test
    void deniesSubmissionWithoutEnrollmentAndRestrictsAccessToWork() throws Exception {
        User peer = createAccount("Иван", "submissions-peer@example.org", UserRole.STUDENT);
        User outsider = createAccount("Ирина", "submissions-outsider@example.org", UserRole.TEACHER);
        enrollments.enrollStudent(courseId, student.getId());
        Long submissionId = submissions.submitAssignment(assignmentId, student.getId(), "Моя работа").id();

        http.perform(post(ASSIGNMENT_SUBMISSIONS, assignmentId).with(authenticatedWrite(peer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateSubmissionRequest("Другая работа"))))
                .andExpect(status().isForbidden());
        http.perform(get("/api/submissions/{id}", submissionId).headers(basic(peer.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
        http.perform(get(ASSIGNMENT_SUBMISSIONS, assignmentId).headers(basic(outsider.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
        http.perform(put(SUBMISSION_GRADE, submissionId).with(authenticatedWrite(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new GradeSubmissionRequest(20, null))))
                .andExpect(status().isForbidden());
        http.perform(get("/api/me/submissions").headers(basic(peer.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsBlankWorkAndDuplicateSubmission() throws Exception {
        enrollments.enrollStudent(courseId, student.getId());
        RequestPostProcessor credentials = authenticatedWrite(student);
        http.perform(post(ASSIGNMENT_SUBMISSIONS, assignmentId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateSubmissionRequest(" "))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("content"));
        String work = json.writeValueAsString(new CreateSubmissionRequest("Решение"));
        http.perform(post(ASSIGNMENT_SUBMISSIONS, assignmentId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON).content(work))
                .andExpect(status().isCreated());
        http.perform(post(ASSIGNMENT_SUBMISSIONS, assignmentId).with(credentials)
                        .contentType(MediaType.APPLICATION_JSON).content(work))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void scoreCannotExceedAssignmentMaximum() throws Exception {
        enrollments.enrollStudent(courseId, student.getId());
        Long submissionId = submissions.submitAssignment(assignmentId, student.getId(), "Решение").id();
        http.perform(put(SUBMISSION_GRADE, submissionId).with(authenticatedWrite(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new GradeSubmissionRequest(21, null))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsFractionalGradeAndPreservesExistingGrade() throws Exception {
        enrollments.enrollStudent(courseId, student.getId());
        Long submissionId = submissions.submitAssignment(assignmentId, student.getId(), "Решение").id();
        SubmissionDetails graded = submissions.gradeSubmission(submissionId, teacher.getId(), 18, "Добавь пример");

        http.perform(put(SUBMISSION_GRADE, submissionId).with(authenticatedWrite(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"score":19.5,"feedback":"Новый комментарий"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("score"))
                .andExpect(jsonPath("$.errors[0].message").value("Укажите целое число"));

        assertThat(submissions.getSubmission(submissionId, student.getId())).isEqualTo(graded);
    }

    private User createAccount(String name, String email, UserRole role) {
        return users.save(new User(name, email, role, passwords.encode(PASSWORD)));
    }

    private RequestPostProcessor authenticatedWrite(User user) throws Exception {
        return ApiTestRequests.authenticatedWrite(http, json, user.getEmail(), PASSWORD);
    }
}
