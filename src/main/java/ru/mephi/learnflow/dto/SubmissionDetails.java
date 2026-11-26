package ru.mephi.learnflow.dto;

import ru.mephi.learnflow.entity.Submission;

import java.time.Instant;

public record SubmissionDetails(Long id, Long assignmentId, Long studentId, String content,
                                Instant submittedAt, Integer score, String feedback) {

    public static SubmissionDetails from(Submission submission) {
        return new SubmissionDetails(submission.getId(), submission.getAssignment().getId(),
                submission.getStudent().getId(), submission.getContent(), submission.getSubmittedAt(),
                submission.getScore(), submission.getFeedback());
    }
}
