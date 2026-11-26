package ru.mephi.learnflow.dto;

import ru.mephi.learnflow.entity.QuizSubmission;

import java.time.Instant;
import java.util.List;

public record QuizAttemptDetails(Long id, Long quizId, Long studentId, int score, Instant takenAt,
                                 List<Answer> answers) {

    public static QuizAttemptDetails from(QuizSubmission attempt) {
        List<Answer> answers = attempt.getAnswers().stream()
                .map(answer -> new Answer(answer.getQuestion().getId(), answer.getOption().getId()))
                .toList();
        return new QuizAttemptDetails(attempt.getId(), attempt.getQuiz().getId(), attempt.getStudent().getId(),
                attempt.getScore(), attempt.getTakenAt(), answers);
    }

    public record Answer(Long questionId, Long optionId) {
    }
}
