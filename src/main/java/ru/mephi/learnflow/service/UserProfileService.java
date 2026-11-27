package ru.mephi.learnflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.dto.UserProfileDetails;
import ru.mephi.learnflow.entity.Profile;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.exception.ResourceNotFoundException;
import ru.mephi.learnflow.repository.ProfileRepository;
import ru.mephi.learnflow.repository.UserRepository;

@Service
public class UserProfileService {

    private final UserRepository users;
    private final ProfileRepository profiles;

    public UserProfileService(UserRepository users, ProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public UserProfileDetails getProfile(Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь " + userId + " не найден"));
        Profile profile = profiles.findById(userId).orElse(null);
        return new UserProfileDetails(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                profile == null ? null : profile.getBio(), profile == null ? null : profile.getAvatarUrl());
    }
}
