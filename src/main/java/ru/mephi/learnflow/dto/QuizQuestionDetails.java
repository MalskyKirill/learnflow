package ru.mephi.learnflow.dto;

import ru.mephi.learnflow.entity.Question;

import java.util.List;

public record QuizQuestionDetails(Long id, String text, List<Option> options) {

    public static QuizQuestionDetails from(Question question) {
        List<Option> options = question.getOptions().stream()
                .map(option -> new Option(option.getId(), option.getText(), option.isCorrect()))
                .toList();
        return new QuizQuestionDetails(question.getId(), question.getText(), options);
    }

    public record Option(Long id, String text, boolean correct) {
    }
}
