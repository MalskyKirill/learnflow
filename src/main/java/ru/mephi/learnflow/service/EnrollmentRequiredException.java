package ru.mephi.learnflow.service;

public class EnrollmentRequiredException extends RuntimeException {

    public EnrollmentRequiredException(Long courseId) {
        super("Для отправки решения нужно записаться на курс " + courseId);
    }
}
