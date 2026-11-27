package ru.mephi.learnflow.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.learnflow.exception.AlreadyEnrolledException;
import ru.mephi.learnflow.exception.AlreadySubmittedException;
import ru.mephi.learnflow.exception.CourseInUseException;
import ru.mephi.learnflow.exception.CourseTeacherRequiredException;
import ru.mephi.learnflow.exception.EnrollmentRequiredException;
import ru.mephi.learnflow.exception.InvalidInputException;
import ru.mephi.learnflow.exception.QuizAlreadyExistsException;
import ru.mephi.learnflow.exception.ResourceNotFoundException;
import ru.mephi.learnflow.service.CourseService;

import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseController.class)
@Import(ApiExceptionHandlerTest.ValidationController.class)
@ActiveProfiles("test")
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc http;

    @MockitoBean
    private CourseService courses;

    @ParameterizedTest
    @MethodSource("domainErrors")
    void mapsDomainErrorsToProblemJson(RuntimeException exception, HttpStatus expectedStatus) throws Exception {
        when(courses.listCourses()).thenThrow(exception);

        http.perform(get("/api/courses"))
                .andExpect(status().is(expectedStatus.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus.value()))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andExpect(jsonPath("$.instance").value("/api/courses"));
    }

    static Stream<Arguments> domainErrors() {
        return Stream.of(
                Arguments.of(new InvalidInputException("Проверьте название курса"), HttpStatus.BAD_REQUEST),
                Arguments.of(new CourseTeacherRequiredException(1L), HttpStatus.FORBIDDEN),
                Arguments.of(new EnrollmentRequiredException(1L), HttpStatus.FORBIDDEN),
                Arguments.of(new ResourceNotFoundException("Курс 1 не найден"), HttpStatus.NOT_FOUND),
                Arguments.of(new AlreadyEnrolledException(1L, 2L, null), HttpStatus.CONFLICT),
                Arguments.of(new AlreadySubmittedException(1L, null), HttpStatus.CONFLICT),
                Arguments.of(new CourseInUseException(1L), HttpStatus.CONFLICT),
                Arguments.of(new QuizAlreadyExistsException(1L), HttpStatus.CONFLICT));
    }

    @Test
    void includesFieldDetailsForInvalidRequestBody() throws Exception {
        http.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("Укажите название"));
    }

    @Test
    void returnsProblemJsonForMalformedRequestBody() throws Exception {
        http.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Ожидается корректное тело JSON"));
    }

    @TestComponent
    @RestController
    static class ValidationController {

        @PostMapping("/test/validation")
        void validate(@Valid @RequestBody Request request) {
        }
    }

    record Request(@NotBlank(message = "Укажите название") String title) {
    }
}
