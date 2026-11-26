package ru.mephi.learnflow.service;

import ru.mephi.learnflow.entity.Quiz;

public record QuizDetails(Long id, Long moduleId, String title) {

    static QuizDetails from(Quiz quiz) {
        return new QuizDetails(quiz.getId(), quiz.getModule().getId(), quiz.getTitle());
    }
}
