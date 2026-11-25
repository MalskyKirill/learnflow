package ru.mephi.learnflow.service;

import ru.mephi.learnflow.entity.Course;

public record CourseDetails(Long id, String title, String description, Long categoryId, Long teacherId) {

    static CourseDetails from(Course course) {
        return new CourseDetails(course.getId(), course.getTitle(), course.getDescription(),
                course.getCategory().getId(), course.getTeacher().getId());
    }
}
