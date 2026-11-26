package ru.mephi.learnflow.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.QuizRepository;
import ru.mephi.learnflow.repository.QuizSubmissionRepository;
import ru.mephi.learnflow.repository.UserRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QuizWorkflowTest {

    @Autowired
    private QuizService quizzes;

    @Autowired
    private CourseService courses;

    @Autowired
    private CourseContentService content;

    @Autowired
    private EnrollmentService enrollments;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizSubmissionRepository attempts;

    private Long categoryId;
    private Long teacherId;
    private Long studentId;
    private Long courseId;
    private Long quizId;

    @Test
    void studentAndTeacherSeeBothAttemptsAfterQuizIsCompleted() {
        categoryId = categories.save(new Category("Практика JPA")).getId();
        teacherId = users.save(new User("Мария", "quiz-workflow-teacher@example.org", UserRole.TEACHER)).getId();
        studentId = users.save(new User("Анна", "quiz-workflow-student@example.org", UserRole.STUDENT)).getId();
        courseId = courses.createCourse("Hibernate", null, categoryId, teacherId).id();
        Long moduleId = content.addModule(courseId, "Основы ORM").id();
        QuizDetails quiz = quizzes.createQuiz(moduleId, teacherId, "Связи и загрузка");
        quizId = quiz.id();
        quizzes.addQuestion(quizId, teacherId, "Какая аннотация описывает связь многие к одному?", List.of(
                new QuizOptionDraft("@OneToMany", false), new QuizOptionDraft("@ManyToOne", true)));
        quizzes.addQuestion(quizId, teacherId, "Как отложить загрузку?", List.of(
                new QuizOptionDraft("LAZY", true), new QuizOptionDraft("EAGER", false)));
        enrollments.enrollStudent(courseId, studentId);

        QuizView view = quizzes.getQuizForStudent(quizId, studentId);
        assertThat(view.quiz()).isEqualTo(quiz);
        assertThat(view.questions()).extracting(QuizView.Question::text)
                .containsExactly("Какая аннотация описывает связь многие к одному?", "Как отложить загрузку?");
        QuizView.Question relation = view.questions().getFirst();
        QuizView.Question loading = view.questions().getLast();
        Long wrongRelation = relation.options().getFirst().id();
        Long rightRelation = relation.options().getLast().id();
        Long lazy = loading.options().getFirst().id();

        QuizAttemptDetails first = quizzes.submitQuiz(quizId, studentId, Map.of(relation.id(), wrongRelation));
        QuizAttemptDetails retry = quizzes.submitQuiz(quizId, studentId,
                Map.of(relation.id(), rightRelation, loading.id(), lazy));

        assertThat(first.score()).isZero();
        assertThat(retry.score()).isEqualTo(2);
        assertThat(first.id()).isNotEqualTo(retry.id());
        assertThat(first.answers()).containsExactly(new QuizAttemptDetails.Answer(relation.id(), wrongRelation));
        assertThat(retry.answers()).containsExactly(
                new QuizAttemptDetails.Answer(relation.id(), rightRelation),
                new QuizAttemptDetails.Answer(loading.id(), lazy));
        assertThat(quizzes.listStudentAttempts(studentId)).containsExactly(retry, first);
        assertThat(quizzes.listQuizAttempts(quizId, teacherId)).containsExactly(retry, first);
    }

    @AfterEach
    void removeFixtures() {
        if (quizId != null) {
            attempts.deleteAll(attempts.findAllByQuiz_IdOrderByTakenAtDescIdDesc(quizId));
            quizRepository.deleteById(quizId);
        }
        if (courseId != null) {
            enrollments.unenrollStudent(courseId, studentId);
            courses.deleteCourse(courseId);
        }
        if (studentId != null) {
            users.deleteById(studentId);
        }
        if (teacherId != null) {
            users.deleteById(teacherId);
        }
        if (categoryId != null) {
            categories.deleteById(categoryId);
        }
    }
}
