package ru.mephi.learnflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateLessonRequest(@NotBlank @Size(max = 255) String title, String content) {
}
