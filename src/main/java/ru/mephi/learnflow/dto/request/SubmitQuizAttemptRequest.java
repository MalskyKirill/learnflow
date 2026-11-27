package ru.mephi.learnflow.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

public record SubmitQuizAttemptRequest(
        @NotNull Map<@NotNull @Positive Long, @NotNull @Positive Long> answers) {
}
