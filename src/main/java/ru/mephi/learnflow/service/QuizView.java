package ru.mephi.learnflow.service;

import ru.mephi.learnflow.entity.Quiz;

import java.util.List;

public record QuizView(QuizDetails quiz, List<Question> questions) {

    static QuizView from(Quiz quiz) {
        List<Question> questions = quiz.getQuestions().stream()
                .map(question -> new Question(question.getId(), question.getText(), question.getOptions().stream()
                        .map(option -> new Option(option.getId(), option.getText()))
                        .toList()))
                .toList();
        return new QuizView(QuizDetails.from(quiz), questions);
    }

    public record Question(Long id, String text, List<Option> options) {
    }

    public record Option(Long id, String text) {
    }
}
