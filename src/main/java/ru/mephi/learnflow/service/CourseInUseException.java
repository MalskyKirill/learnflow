package ru.mephi.learnflow.service;

public class CourseInUseException extends RuntimeException {

    public CourseInUseException(Long courseId) {
        this(courseId, null);
    }

    public CourseInUseException(Long courseId, Throwable cause) {
        super("Нельзя удалить курс " + courseId + ", пока существуют связанные данные", cause);
    }
}
