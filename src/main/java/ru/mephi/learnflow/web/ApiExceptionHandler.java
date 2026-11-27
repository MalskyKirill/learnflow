package ru.mephi.learnflow.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.mephi.learnflow.exception.AlreadyEnrolledException;
import ru.mephi.learnflow.exception.AlreadySubmittedException;
import ru.mephi.learnflow.exception.CourseInUseException;
import ru.mephi.learnflow.exception.CourseTeacherRequiredException;
import ru.mephi.learnflow.exception.EnrollmentRequiredException;
import ru.mephi.learnflow.exception.InvalidInputException;
import ru.mephi.learnflow.exception.QuizAlreadyExistsException;
import ru.mephi.learnflow.exception.ResourceNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidInputException.class)
    public ProblemDetail invalidInput(InvalidInputException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({CourseTeacherRequiredException.class, EnrollmentRequiredException.class})
    public ProblemDetail forbidden(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({AlreadyEnrolledException.class, AlreadySubmittedException.class, CourseInUseException.class,
            QuizAlreadyExistsException.class})
    public ProblemDetail conflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Проверьте поля запроса");
        problem.setProperty("errors", exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ValidationError(
                        error instanceof FieldError field ? field.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Ожидается корректное тело JSON");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    private record ValidationError(String field, String message) {
    }
}
