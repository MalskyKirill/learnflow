package ru.mephi.learnflow.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.UserRepository;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SecurityProblemHandler problems) throws Exception {
        CookieCsrfTokenRepository csrfTokens = new CookieCsrfTokenRepository();
        csrfTokens.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));
        return http.authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/{courseId:[0-9]+}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/courses").hasRole(UserRole.TEACHER.name())
                        .requestMatchers("/api/courses/{courseId}/enrollment", "/api/me/courses")
                        .hasRole(UserRole.STUDENT.name())
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(problems))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(email)
                .filter(user -> user.getPasswordHash() != null && !user.getPasswordHash().isBlank())
                .map(LearnFlowPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Учётная запись недоступна"));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
