package ru.mephi.learnflow.exception;

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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

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
            CourseContentInUseException.class, QuizAlreadyExistsException.class})
    public ProblemDetail conflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ValidationError> errors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ValidationError(
                        error instanceof FieldError field ? field.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return handleExceptionInternal(exception, validationProblem(status, errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception.isForReturnValue()) {
            return super.handleHandlerMethodValidationException(exception, headers, status, request);
        }
        List<ValidationError> errors = exception.getParameterValidationResults().stream()
                .flatMap(parameter -> parameter.getResolvableErrors().stream()
                        .map(error -> new ValidationError(
                                error instanceof FieldError field ? field.getField()
                                        : parameter.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(exception, validationProblem(status, errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Ожидается корректное тело JSON");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    private ProblemDetail validationProblem(HttpStatusCode status, List<ValidationError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Проверьте поля запроса");
        problem.setProperty("errors", errors);
        return problem;
    }

    private record ValidationError(String field, String message) {
    }
}
