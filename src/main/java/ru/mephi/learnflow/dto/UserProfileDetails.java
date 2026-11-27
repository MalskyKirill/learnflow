package ru.mephi.learnflow.dto;

import ru.mephi.learnflow.entity.UserRole;

public record UserProfileDetails(Long id, String name, String email, UserRole role, String bio, String avatarUrl) {
}
