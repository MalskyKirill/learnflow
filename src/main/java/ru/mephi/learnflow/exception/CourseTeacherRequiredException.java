package ru.mephi.learnflow.exception;

public class CourseTeacherRequiredException extends RuntimeException {

    public CourseTeacherRequiredException(Long courseId) {
        super("Это действие доступно только преподавателю курса " + courseId);
    }
}
