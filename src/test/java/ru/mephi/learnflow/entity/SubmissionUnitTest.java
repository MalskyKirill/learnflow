package ru.mephi.learnflow.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.mephi.learnflow.exception.InvalidInputException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmissionUnitTest {

    private static final int MAX_SCORE = 20;

    private final User student = new User("Анна", "student@example.org", UserRole.STUDENT);
    private final Assignment assignment;

    SubmissionUnitTest() {
        User teacher = new User("Мария", "teacher@example.org", UserRole.TEACHER);
        Course course = new Course("Основы JPA", null, new Category("Базы данных"), teacher);
        Lesson lesson = course.addModule("Связи").addLesson("Многие к одному", null);
        assignment = lesson.addAssignment("Опиши связь", null, null, MAX_SCORE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t\n"})
    void rejectsBlankSubmission(String content) {
        assertThatThrownBy(() -> assignment.addSubmission(student, content))
                .isInstanceOf(InvalidInputException.class);
        assertThat(assignment.getSubmissions()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "STUDENT", mode = EnumSource.Mode.EXCLUDE)
    void onlyStudentCanSubmitWork(UserRole role) {
        User author = new User("Автор", "author@example.org", role);

        assertThatThrownBy(() -> assignment.addSubmission(author, "Связь ManyToOne"))
                .isInstanceOf(InvalidInputException.class);
        assertThat(assignment.getSubmissions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, MAX_SCORE})
    void acceptsScoresAtBothBounds(int score) {
        Submission submission = assignment.addSubmission(student, "Связь ManyToOne");
        assertThat(submission.getScore()).isNull();

        submission.grade(score, "Проверено");

        assertThat(submission.getScore()).isEqualTo(score);
        assertThat(submission.getFeedback()).isEqualTo("Проверено");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, MAX_SCORE + 1})
    void invalidRegradePreservesScoreAndFeedback(int score) {
        Submission submission = assignment.addSubmission(student, "Связь ManyToOne");
        submission.grade(15, "Укажи внешний ключ");

        assertThatThrownBy(() -> submission.grade(score, "Новый комментарий"))
                .isInstanceOf(InvalidInputException.class);
        assertThat(submission.getScore()).isEqualTo(15);
        assertThat(submission.getFeedback()).isEqualTo("Укажи внешний ключ");
    }

    @Test
    void gradingUsesCurrentAssignmentMaximum() {
        Submission submission = assignment.addSubmission(student, "Связь ManyToOne");
        assignment.updateDetails("Опиши связь", null, null, 10);

        assertThatThrownBy(() -> submission.grade(11, null)).isInstanceOf(InvalidInputException.class);
        submission.grade(10, null);

        assertThat(submission.getScore()).isEqualTo(10);
    }
}
