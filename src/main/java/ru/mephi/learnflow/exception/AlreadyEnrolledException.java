package ru.mephi.learnflow.exception;

public class AlreadyEnrolledException extends RuntimeException {

    public AlreadyEnrolledException(Long studentId, Long courseId, Throwable cause) {
        super("Студент " + studentId + " уже записан на курс " + courseId, cause);
    }
}
