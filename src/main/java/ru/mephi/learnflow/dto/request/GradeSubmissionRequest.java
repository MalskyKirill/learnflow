package ru.mephi.learnflow.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record GradeSubmissionRequest(@NotNull @PositiveOrZero Integer score, String feedback) {
}
