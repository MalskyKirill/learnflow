package ru.mephi.learnflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuizRequest(@NotBlank @Size(max = 255) String title) {
}
