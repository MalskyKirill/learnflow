package ru.mephi.learnflow.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.ProfileRepository;
import ru.mephi.learnflow.repository.UserRepository;
import ru.mephi.learnflow.security.LearnFlowPrincipal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///learnflow_demo_test",
        "DEMO_TEACHER_PASSWORD=test-teacher-password",
        "DEMO_STUDENT_PASSWORD=test-student-password"
})
@ActiveProfiles({"test", "demo"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoDataInitializerTest {

    @Autowired
    private DemoDataInitializer initializer;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserDetailsService userDetails;

    @Autowired
    private PasswordEncoder passwords;

    @Test
    void createsAccountsThatCanLogInAndSeededCategory() {
        assertAccount(DemoDataInitializer.TEACHER_EMAIL, UserRole.TEACHER, "test-teacher-password");
        assertAccount(DemoDataInitializer.STUDENT_EMAIL, UserRole.STUDENT, "test-student-password");
        assertThat(categories.findByName(DemoDataInitializer.CATEGORY_NAME)).isPresent();
    }

    @Test
    void rerunningInitializerKeepsExistingDemoData() {
        User teacher = users.findByEmail(DemoDataInitializer.TEACHER_EMAIL).orElseThrow();
        Long categoryId = categories.findByName(DemoDataInitializer.CATEGORY_NAME).orElseThrow().getId();

        initializer.run(new DefaultApplicationArguments());

        assertThat(users.count()).isEqualTo(2);
        assertThat(profiles.count()).isEqualTo(2);
        assertThat(categories.count()).isOne();
        User existingTeacher = users.findByEmail(DemoDataInitializer.TEACHER_EMAIL).orElseThrow();
        assertThat(existingTeacher.getId()).isEqualTo(teacher.getId());
        assertThat(existingTeacher.getPasswordHash()).isEqualTo(teacher.getPasswordHash());
        assertThat(categories.findByName(DemoDataInitializer.CATEGORY_NAME).orElseThrow().getId())
                .isEqualTo(categoryId);
    }

    private void assertAccount(String email, UserRole role, String rawPassword) {
        User account = users.findByEmail(email).orElseThrow();
        assertThat(account.getRole()).isEqualTo(role);
        assertThat(profiles.findById(account.getId())).isPresent();

        UserDetails principal = userDetails.loadUserByUsername(email);
        assertThat(principal).isInstanceOfSatisfying(LearnFlowPrincipal.class,
                authenticated -> assertThat(authenticated.getUserId()).isEqualTo(account.getId()));
        assertThat(principal.getPassword()).isNotEqualTo(rawPassword);
        assertThat(passwords.matches(rawPassword, principal.getPassword())).isTrue();
    }
}
