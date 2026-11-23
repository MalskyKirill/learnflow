package ru.mephi.learnflow.entity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserProfilePersistenceTest {

    @Autowired
    private EntityManager entities;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loadsProfileByUserIdAndFetchesUserOnDemand() {
        User user = new User("Анна", "anna@example.org", UserRole.TEACHER);
        entities.persist(user);
        entities.persist(new Profile(user, "Преподаю Java", "/avatars/anna.png"));
        entities.flush();
        entities.clear();

        Profile profile = entities.find(Profile.class, user.getId());
        assertThat(profile.getBio()).isEqualTo("Преподаю Java");
        assertThat(profile.getAvatarUrl()).isEqualTo("/avatars/anna.png");
        assertThat(entities.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(profile, "user")).isFalse();
        assertThat(profile.getUser().getRole()).isEqualTo(UserRole.TEACHER);
    }

    @Test
    void rejectsDuplicateEmail() {
        User user = new User("Анна", "anna@example.org", UserRole.STUDENT);
        entities.persist(user);
        entities.flush();

        assertThatThrownBy(() -> jdbc.update(
                "insert into users (name, email, role) values (?, ?, ?)",
                "Мария", user.getEmail(), "STUDENT"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void rejectsSecondProfileForSameUser() {
        User user = new User("Анна", "anna@example.org", UserRole.STUDENT);
        entities.persist(user);
        entities.persist(new Profile(user, null, null));
        entities.flush();

        assertThatThrownBy(() -> jdbc.update(
                "insert into profiles (user_id) values (?)", user.getId()))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
