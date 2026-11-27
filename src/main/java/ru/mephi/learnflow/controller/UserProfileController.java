package ru.mephi.learnflow.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.learnflow.dto.UserProfileDetails;
import ru.mephi.learnflow.security.LearnFlowPrincipal;
import ru.mephi.learnflow.service.UserProfileService;

@RestController
public class UserProfileController {

    private final UserProfileService profiles;

    public UserProfileController(UserProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/me")
    public UserProfileDetails me(@AuthenticationPrincipal LearnFlowPrincipal principal) {
        return profiles.getProfile(principal.getUserId());
    }
}
