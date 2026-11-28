package ru.mephi.learnflow.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mephi.learnflow.dto.QuizAttemptDetails;
import ru.mephi.learnflow.dto.QuizDetails;
import ru.mephi.learnflow.dto.QuizOptionDraft;
import ru.mephi.learnflow.dto.QuizQuestionDetails;
import ru.mephi.learnflow.dto.QuizView;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.exception.CourseContentInUseException;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.QuizRepository;
import ru.mephi.learnflow.repository.QuizSubmissionRepository;
import ru.mephi.learnflow.repository.UserRepository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoryId;
    private Long teacherId;
    private Long studentId;
    private Long courseId;
    private Long quizId;

    @Test
    void studentAndTeacherSeeBothAttemptsAfterQuizIsCompleted() {
        QuizDetails quiz = createQuizWithEnrolledStudent();
        quizzes.addQuestion(quizId, teacherId, "Какая аннотация описывает связь многие к одному?", List.of(
                new QuizOptionDraft("@OneToMany", false), new QuizOptionDraft("@ManyToOne", true)));
        quizzes.addQuestion(quizId, teacherId, "Как отложить загрузку?", List.of(
                new QuizOptionDraft("LAZY", true), new QuizOptionDraft("EAGER", false)));
        QuizView view = quizzes.getQuiz(quizId, studentId);
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

    @Test
    void questionDeletionWaitsForSubmissionAndPreservesCommittedAnswer() throws Exception {
        createQuizWithEnrolledStudent();
        QuizQuestionDetails question = quizzes.addQuestion(quizId, teacherId, "Как отложить загрузку?",
                List.of(new QuizOptionDraft("LAZY", true), new QuizOptionDraft("EAGER", false)));
        Long selected = question.options().getFirst().id();
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CompletableFuture<Integer> submittingBackend = new CompletableFuture<>();
        CompletableFuture<Integer> deletingBackend = new CompletableFuture<>();
        CountDownLatch commitAttempt = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var submission = executor.submit(() -> transactions.execute(status -> {
                QuizAttemptDetails attempt = quizzes.submitQuiz(quizId, studentId, Map.of(question.id(), selected));
                submittingBackend.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                awaitCommitSignal(commitAttempt);
                return attempt;
            }));

            try {
                int submitPid = submittingBackend.get(10, TimeUnit.SECONDS);
                var deletion = executor.submit(() -> transactions.executeWithoutResult(status -> {
                    deletingBackend.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    quizzes.deleteQuestion(quizId, question.id(), teacherId);
                }));
                int deletePid = deletingBackend.get(10, TimeUnit.SECONDS);
                await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.TRUE.equals(
                        jdbc.queryForObject("select ? = any(pg_blocking_pids(?))", Boolean.class, submitPid, deletePid)));

                commitAttempt.countDown();
                QuizAttemptDetails saved = submission.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(() -> deletion.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(CourseContentInUseException.class);
                assertThat(quizzes.getQuestionForTeacher(quizId, question.id(), teacherId)).isEqualTo(question);
                assertThat(quizzes.listStudentAttempts(studentId)).containsExactly(saved);
            } finally {
                commitAttempt.countDown();
            }
        }
    }

    private QuizDetails createQuizWithEnrolledStudent() {
        categoryId = categories.save(new Category("Практика JPA")).getId();
        teacherId = users.save(new User("Мария", "quiz-workflow-teacher@example.org", UserRole.TEACHER)).getId();
        studentId = users.save(new User("Анна", "quiz-workflow-student@example.org", UserRole.STUDENT)).getId();
        courseId = courses.createCourse("Hibernate", null, categoryId, teacherId).id();
        Long moduleId = content.addModule(courseId, teacherId, "Основы ORM").id();
        QuizDetails quiz = quizzes.createQuiz(moduleId, teacherId, "Связи и загрузка");
        quizId = quiz.id();
        enrollments.enrollStudent(courseId, studentId);
        return quiz;
    }

    private void awaitCommitSignal(CountDownLatch signal) {
        try {
            assertThat(signal.await(30, TimeUnit.SECONDS)).as("разрешение завершить попытку").isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @AfterEach
    void removeFixtures() {
        if (quizId != null) {
            attempts.deleteAll(attempts.findAllByQuiz_IdOrderByTakenAtDescIdDesc(quizId));
            quizRepository.deleteById(quizId);
        }
        if (courseId != null) {
            enrollments.unenrollStudent(courseId, studentId);
            courses.deleteCourse(courseId, teacherId);
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
