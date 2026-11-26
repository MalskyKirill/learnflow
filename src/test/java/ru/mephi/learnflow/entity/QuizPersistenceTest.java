package ru.mephi.learnflow.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuizPersistenceTest {

    @Autowired
    private EntityManager entities;

    @Autowired
    private JdbcTemplate jdbc;

    private final Category category = new Category("Базы данных");
    private final User teacher = new User("Мария", "maria@example.org", UserRole.TEACHER);
    private final User student = new User("Анна", "anna@example.org", UserRole.STUDENT);
    private final Course course = new Course("Hibernate", null, category, teacher);
    private final CourseModule module = course.addModule("JPA");
    private final Quiz quiz = new Quiz(module, "Связи сущностей");

    @BeforeEach
    void saveQuizModuleAndUsers() {
        entities.persist(category);
        entities.persist(teacher);
        entities.persist(student);
        entities.persist(course);
    }

    @Test
    void savesQuizWithQuestionsAndRetainsAnswersForEachAttempt() {
        Question question = quiz.addQuestion("Какая аннотация описывает связь многие к одному?");
        AnswerOption correct = question.addOption("@ManyToOne", true);
        AnswerOption incorrect = question.addOption("@OneToMany", false);
        entities.persist(quiz);

        QuizSubmission first = new QuizSubmission(quiz, student, 0);
        first.addAnswer(incorrect);
        entities.persist(first);
        QuizSubmission second = new QuizSubmission(quiz, student, 1);
        second.addAnswer(correct);
        entities.persist(second);
        entities.flush();
        entities.clear();

        Quiz loadedQuiz = entities.find(Quiz.class, quiz.getId());
        PersistenceUnitUtil persistence = entities.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(persistence.isLoaded(loadedQuiz, "questions")).isFalse();
        assertThat(loadedQuiz.getModule().getId()).isEqualTo(module.getId());
        Question loadedQuestion = loadedQuiz.getQuestions().getFirst();
        assertThat(loadedQuestion.getOptions()).extracting(AnswerOption::isCorrect)
                .containsExactly(true, false);

        QuizSubmission firstAttempt = entities.find(QuizSubmission.class, first.getId());
        QuizSubmission secondAttempt = entities.find(QuizSubmission.class, second.getId());
        assertThat(firstAttempt.getAnswers().getFirst().getOption().getText()).isEqualTo("@OneToMany");
        assertThat(firstAttempt.getScore()).isZero();
        assertThat(secondAttempt.getScore()).isEqualTo(1);
        assertThat(secondAttempt.getQuiz().getId()).isEqualTo(quiz.getId());
        assertThat(secondAttempt.getStudent().getId()).isEqualTo(student.getId());
        assertThat(secondAttempt.getTakenAt()).isNotNull();
        assertThat(secondAttempt.getAnswers().getFirst().getQuestion().getId()).isEqualTo(question.getId());
        assertThat(secondAttempt.getAnswers().getFirst().getOption().getText()).isEqualTo("@ManyToOne");

        assertThatThrownBy(() -> jdbc.update("""
                insert into student_answers (submission_id, question_id, option_id) values (?, ?, ?)
                """, second.getId(), question.getId(), incorrect.getId()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void rejectsSecondQuizForSameModule() {
        entities.persist(quiz);
        entities.flush();

        assertThatThrownBy(() -> jdbc.update(
                "insert into quizzes (module_id, title) values (?, ?)", module.getId(), "Второй тест"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void rejectsOptionFromAnotherQuiz() {
        Quiz otherQuiz = new Quiz(course.addModule("SQL"), "Запросы");
        AnswerOption foreignOption = otherQuiz.addQuestion("Что читает данные?").addOption("SELECT", true);
        QuizSubmission attempt = new QuizSubmission(quiz, student, 0);

        assertThatIllegalArgumentException().isThrownBy(() -> attempt.addAnswer(foreignOption));
    }

    @Test
    void resolvesOnlySelectedOptionsInQuestionOrder() {
        Question relation = quiz.addQuestion("Какая связь нужна?");
        AnswerOption manyToOne = relation.addOption("@ManyToOne", true);
        Question fetch = quiz.addQuestion("Как отложить загрузку?");
        AnswerOption lazy = fetch.addOption("LAZY", true);
        entities.persist(quiz);
        entities.flush();
        entities.clear();

        Quiz loaded = entities.find(Quiz.class, quiz.getId());
        Map<Long, Long> answers = new LinkedHashMap<>();
        answers.put(fetch.getId(), lazy.getId());
        answers.put(relation.getId(), manyToOne.getId());

        assertThat(loaded.resolveSelectedOptions(answers)).extracting(AnswerOption::getId)
                .containsExactly(manyToOne.getId(), lazy.getId());
        assertThat(answers).hasSize(2);
        assertThat(loaded.resolveSelectedOptions(Map.of(fetch.getId(), lazy.getId())))
                .extracting(AnswerOption::getId).containsExactly(lazy.getId());
        assertThat(loaded.resolveSelectedOptions(Map.of())).isEmpty();
    }

    @Test
    void rejectsUnknownQuestionsAndOptionsFromAnotherQuestion() {
        Question relation = quiz.addQuestion("Какая связь нужна?");
        AnswerOption manyToOne = relation.addOption("@ManyToOne", true);
        Question fetch = quiz.addQuestion("Как отложить загрузку?");
        AnswerOption lazy = fetch.addOption("LAZY", true);
        CourseModule otherModule = course.addModule("SQL");
        entities.flush();
        Quiz otherQuiz = new Quiz(otherModule, "Запросы");
        Question query = otherQuiz.addQuestion("Что читает данные?");
        AnswerOption select = query.addOption("SELECT", true);
        entities.persist(quiz);
        entities.persist(otherQuiz);
        entities.flush();
        entities.clear();

        Quiz loaded = entities.find(Quiz.class, quiz.getId());
        assertThatIllegalArgumentException().isThrownBy(
                () -> loaded.resolveSelectedOptions(Map.of(query.getId(), select.getId())));
        assertThatIllegalArgumentException().isThrownBy(
                () -> loaded.resolveSelectedOptions(Map.of(relation.getId(), lazy.getId())));
        assertThatIllegalArgumentException().isThrownBy(
                () -> loaded.resolveSelectedOptions(Map.of(relation.getId(), select.getId())));
        assertThatIllegalArgumentException().isThrownBy(
                () -> loaded.resolveSelectedOptions(Map.of(relation.getId(), Long.MAX_VALUE)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> loaded.resolveSelectedOptions(Collections.singletonMap(relation.getId(), null)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> loaded.resolveSelectedOptions(Collections.singletonMap(null, manyToOne.getId())));
    }
}
