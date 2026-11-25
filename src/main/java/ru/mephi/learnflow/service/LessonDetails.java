package ru.mephi.learnflow.service;

import ru.mephi.learnflow.entity.Lesson;

public record LessonDetails(Long id, Long moduleId, String title, String content, int position) {

    static LessonDetails from(Lesson lesson) {
        return new LessonDetails(lesson.getId(), lesson.getModule().getId(),
                lesson.getTitle(), lesson.getContent(), lesson.getPosition());
    }
}
