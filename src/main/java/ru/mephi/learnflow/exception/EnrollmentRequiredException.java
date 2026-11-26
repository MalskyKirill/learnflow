package ru.mephi.learnflow.exception;

public class EnrollmentRequiredException extends RuntimeException {

    public EnrollmentRequiredException(Long courseId) {
        this(courseId, "отправки решения");
    }

    public EnrollmentRequiredException(Long courseId, String action) {
        super("Для " + action + " нужно записаться на курс " + courseId);
    }
}
