package ru.mephi.learnflow.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuizQuestionRequest(
        @NotBlank String text,
        @NotNull @Size(min = 2) List<@NotNull @Valid Option> options) {

    public record Option(@NotBlank String text, @NotNull Boolean correct) {
    }
}
