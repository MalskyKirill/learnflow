package ru.mephi.learnflow.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.QuizRepository;
import ru.mephi.learnflow.repository.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuizServiceTest {

    @Autowired
    private QuizService service;

    @Autowired
    private QuizRepository quizzes;

    @Autowired
    private CourseService courses;

    @Autowired
    private CourseContentService content;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entities;

    private Long moduleId;
    private Long teacherId;

    @BeforeEach
    void createCourseAndModule() {
        teacherId = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER)).getId();
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        Long courseId = courses.createCourse("ORM", null, categoryId, teacherId).id();
        moduleId = content.addModule(courseId, "Основы").id();
        entities.clear();
    }

    @Test
    void assignedTeacherCreatesQuizForModule() {
        QuizDetails created = service.createQuiz(moduleId, teacherId, "Связи сущностей");
        entities.clear();

        assertThat(created.id()).isNotNull();
        assertThat(quizzes.findByModule_Id(moduleId)).get()
                .extracting(QuizDetails::from)
                .isEqualTo(new QuizDetails(created.id(), moduleId, "Связи сущностей"));
    }

    @Test
    void rejectsSecondQuizForModule() {
        QuizDetails first = service.createQuiz(moduleId, teacherId, "Связи сущностей");

        assertThatThrownBy(() -> service.createQuiz(moduleId, teacherId, "Повторная викторина"))
                .isInstanceOf(QuizAlreadyExistsException.class);
        assertThat(quizzes.findByModule_Id(moduleId)).get()
                .extracting(QuizDetails::from).isEqualTo(first);
    }

    @Test
    void rejectsAnotherTeacherAndEmptyTitle() {
        Long otherTeacherId = users.save(new User("Ирина", "irina@example.org", UserRole.TEACHER)).getId();

        assertThatThrownBy(() -> service.createQuiz(moduleId, otherTeacherId, "Связи сущностей"))
                .isInstanceOf(CourseTeacherRequiredException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> service.createQuiz(moduleId, teacherId, " "));
        assertThat(quizzes.findByModule_Id(moduleId)).isEmpty();
    }

    @Test
    void savesQuestionAndAllOptionsWithTheirCorrectness() {
        QuizDetails quiz = service.createQuiz(moduleId, teacherId, "Связи сущностей");
        entities.clear();
        QuizQuestionDetails question = service.addQuestion(quiz.id(), teacherId, "Какая аннотация нужна?", List.of(
                new QuizOptionDraft("@OneToMany", false),
                new QuizOptionDraft("@ManyToOne", true),
                new QuizOptionDraft("@OneToOne", false)));
        entities.clear();

        assertThat(question.id()).isNotNull();
        assertThat(question.options()).extracting(QuizQuestionDetails.Option::id).doesNotContainNull();
        assertThat(question.options()).extracting(QuizQuestionDetails.Option::correct).containsExactly(false, true, false);
        assertThat(quizzes.findById(quiz.id()).orElseThrow().getQuestions())
                .extracting(QuizQuestionDetails::from).containsExactly(question);
    }

    @Test
    void rejectsInvalidQuestionsAndLeavesQuizEmpty() {
        Long quizId = service.createQuiz(moduleId, teacherId, "Связи сущностей").id();
        QuizOptionDraft correct = new QuizOptionDraft("@ManyToOne", true);
        QuizOptionDraft incorrect = new QuizOptionDraft("@OneToMany", false);

        assertThatIllegalArgumentException().isThrownBy(
                () -> service.addQuestion(quizId, teacherId, " ", List.of(correct, incorrect)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> service.addQuestion(quizId, teacherId, "Какая аннотация нужна?", List.of(correct)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> service.addQuestion(quizId, teacherId, "Какая аннотация нужна?", List.of(incorrect, incorrect)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> service.addQuestion(quizId, teacherId, "Какая аннотация нужна?", List.of(correct, correct)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.addQuestion(
                quizId, teacherId, "Какая аннотация нужна?", List.of(correct, new QuizOptionDraft(" ", false))));
        assertThat(quizzes.findById(quizId).orElseThrow().getQuestions()).isEmpty();
    }

    @Test
    void rejectsAddingQuestionByAnotherTeacher() {
        Long quizId = service.createQuiz(moduleId, teacherId, "Связи сущностей").id();
        Long otherTeacherId = users.save(new User("Ирина", "irina@example.org", UserRole.TEACHER)).getId();

        assertThatThrownBy(() -> service.addQuestion(quizId, otherTeacherId, "Какая аннотация нужна?", List.of(
                new QuizOptionDraft("@ManyToOne", true), new QuizOptionDraft("@OneToMany", false))))
                .isInstanceOf(CourseTeacherRequiredException.class);
        assertThat(quizzes.findById(quizId).orElseThrow().getQuestions()).isEmpty();
    }
}
