package ru.mephi.learnflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateCourseRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotNull @Positive Long categoryId,
        @Positive Long teacherId) {
}
