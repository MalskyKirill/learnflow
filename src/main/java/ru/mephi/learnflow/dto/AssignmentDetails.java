package ru.mephi.learnflow.dto;

import ru.mephi.learnflow.entity.Assignment;

import java.time.Instant;

public record AssignmentDetails(Long id, Long lessonId, String title, String description, Instant dueAt, int maxScore) {

    public static AssignmentDetails from(Assignment assignment) {
        return new AssignmentDetails(assignment.getId(), assignment.getLesson().getId(), assignment.getTitle(),
                assignment.getDescription(), assignment.getDueAt(), assignment.getMaxScore());
    }
}
