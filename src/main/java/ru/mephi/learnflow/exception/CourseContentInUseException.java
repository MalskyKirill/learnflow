package ru.mephi.learnflow.exception;

public class CourseContentInUseException extends RuntimeException {

    public CourseContentInUseException(String message) {
        super(message);
    }

    public CourseContentInUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
