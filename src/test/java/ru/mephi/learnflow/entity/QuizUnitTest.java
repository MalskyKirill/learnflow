package ru.mephi.learnflow.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.mephi.learnflow.exception.InvalidInputException;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizUnitTest {

    private final User student = new User("Анна", "student@example.org", UserRole.STUDENT);
    private Quiz quiz;
    private AnswerOption correctAssociation;
    private AnswerOption wrongFetchMode;

    @BeforeEach
    void createQuiz() {
        User teacher = new User("Мария", "teacher@example.org", UserRole.TEACHER);
        Course course = new Course("Основы JPA", null, new Category("Базы данных"), teacher);
        quiz = new Quiz(course.addModule("Связи"), "Связи и загрузка");

        Question association = quiz.addQuestion("Как обозначить связь урока с модулем?");
        ReflectionTestUtils.setField(association, "id", 1L);
        correctAssociation = addOption(association, 11L, "@ManyToOne", true);
        addOption(association, 12L, "@OneToMany", false);

        Question fetchMode = quiz.addQuestion("Как отложить загрузку связи?");
        ReflectionTestUtils.setField(fetchMode, "id", 2L);
        addOption(fetchMode, 21L, "LAZY", true);
        wrongFetchMode = addOption(fetchMode, 22L, "EAGER", false);

        Question ownership = quiz.addQuestion("Где хранится внешний ключ?");
        ReflectionTestUtils.setField(ownership, "id", 3L);
        addOption(ownership, 31L, "В таблице уроков", true);
        addOption(ownership, 32L, "В таблице модулей", false);
    }

    @Test
    void scoresCorrectChoicesAndRetainsOnlySubmittedAnswers() {
        Map<Long, Long> answers = Map.of(correctAssociation.getQuestion().getId(), correctAssociation.getId(),
                wrongFetchMode.getQuestion().getId(), wrongFetchMode.getId());

        QuizSubmission result = quiz.gradeAttempt(student, answers);

        assertThat(result.getScore()).isOne();
        assertThat(result.getAnswers()).extracting(StudentAnswer::getOption)
                .containsExactly(correctAssociation, wrongFetchMode);
    }

    @Test
    void scoresAttemptWithNoAnswersAsZero() {
        QuizSubmission result = quiz.gradeAttempt(student, Map.of());

        assertThat(result.getScore()).isZero();
        assertThat(result.getAnswers()).isEmpty();
    }

    @Test
    void rejectsMissingAnswers() {
        assertThatThrownBy(() -> quiz.gradeAttempt(student, null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void rejectsMissingOptionForAnsweredQuestion() {
        Long questionId = correctAssociation.getQuestion().getId();

        assertThatThrownBy(() -> quiz.gradeAttempt(student, Collections.singletonMap(questionId, null)))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void rejectsQuestionOutsideQuiz() {
        assertThatThrownBy(() -> quiz.gradeAttempt(student, Map.of(999L, correctAssociation.getId())))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void rejectsOptionFromAnotherQuestion() {
        Long questionId = correctAssociation.getQuestion().getId();

        assertThatThrownBy(() -> quiz.gradeAttempt(student, Map.of(questionId, wrongFetchMode.getId())))
                .isInstanceOf(InvalidInputException.class);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "STUDENT", mode = EnumSource.Mode.EXCLUDE)
    void onlyStudentCanTakeQuiz(UserRole role) {
        User participant = new User("Участник", "participant@example.org", role);

        assertThatThrownBy(() -> quiz.gradeAttempt(participant, Map.of()))
                .isInstanceOf(InvalidInputException.class);
    }

    private AnswerOption addOption(Question question, Long id, String text, boolean correct) {
        AnswerOption option = question.addOption(text, correct);
        ReflectionTestUtils.setField(option, "id", id);
        return option;
    }
}
