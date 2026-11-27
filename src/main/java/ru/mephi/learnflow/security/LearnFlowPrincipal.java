package ru.mephi.learnflow.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

public final class LearnFlowPrincipal extends User {

    private final Long userId;

    public LearnFlowPrincipal(ru.mephi.learnflow.entity.User user) {
        super(user.getEmail(), user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        this.userId = user.getId();
    }

    public Long getUserId() {
        return userId;
    }
}
