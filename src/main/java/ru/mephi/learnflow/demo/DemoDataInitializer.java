package ru.mephi.learnflow.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.ProfileRepository;
import ru.mephi.learnflow.repository.UserRepository;

@Component
@Profile("demo")
public class DemoDataInitializer implements ApplicationRunner {

    static final String TEACHER_EMAIL = "teacher@demo.learnflow.example";
    static final String STUDENT_EMAIL = "student@demo.learnflow.example";
    static final String CATEGORY_NAME = "ORM и Hibernate";

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final CategoryRepository categories;
    private final DemoCourseInitializer demoCourse;
    private final PasswordEncoder passwords;
    private final String teacherPassword;
    private final String studentPassword;

    public DemoDataInitializer(UserRepository users, ProfileRepository profiles, CategoryRepository categories,
            DemoCourseInitializer demoCourse, PasswordEncoder passwords,
            @Value("${DEMO_TEACHER_PASSWORD:}") String teacherPassword,
            @Value("${DEMO_STUDENT_PASSWORD:}") String studentPassword) {
        this.users = users;
        this.profiles = profiles;
        this.categories = categories;
        this.demoCourse = demoCourse;
        this.passwords = passwords;
        this.teacherPassword = teacherPassword;
        this.studentPassword = studentPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        User teacher = ensureUser("Мария Смирнова", TEACHER_EMAIL, UserRole.TEACHER, teacherPassword,
                "DEMO_TEACHER_PASSWORD");
        User student = ensureUser("Анна Волкова", STUDENT_EMAIL, UserRole.STUDENT, studentPassword,
                "DEMO_STUDENT_PASSWORD");
        ensureProfile(teacher, "Преподаватель курса по ORM и Hibernate");
        ensureProfile(student, "Изучаю Java и JPA");
        Category category = categories.findByName(CATEGORY_NAME)
                .orElseGet(() -> categories.save(new Category(CATEGORY_NAME)));
        demoCourse.initialize(category.getId(), teacher.getId(), student.getId());
    }

    private User ensureUser(String name, String email, UserRole role, String password, String passwordVariable) {
        return users.findForUpdateByEmail(email)
                .map(existing -> {
                    if (existing.getRole() != role || existing.getPasswordHash() == null
                            || existing.getPasswordHash().isBlank()) {
                        throw new IllegalStateException("Демо-адрес " + email
                                + " уже занят учётной записью с несовместимой ролью или без пароля");
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    if (password.isBlank()) {
                        throw new IllegalStateException("Задай " + passwordVariable + " для создания демо-учётной записи");
                    }
                    return users.save(new User(name, email, role, passwords.encode(password)));
                });
    }

    private void ensureProfile(User user, String bio) {
        if (!profiles.existsById(user.getId())) {
            profiles.save(new ru.mephi.learnflow.entity.Profile(user, bio, null));
        }
    }
}
