package ru.mephi.learnflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLessonRequest(@NotBlank @Size(max = 255) String title, String content) {
}
