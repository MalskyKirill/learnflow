package ru.mephi.learnflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateAssignmentRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        Instant dueAt,
        @NotNull @Positive Integer maxScore) {
}
