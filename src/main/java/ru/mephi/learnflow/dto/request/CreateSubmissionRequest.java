package ru.mephi.learnflow.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateSubmissionRequest(@NotBlank String content) {
}
