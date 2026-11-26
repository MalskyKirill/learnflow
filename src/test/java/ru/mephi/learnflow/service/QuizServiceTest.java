package ru.mephi.learnflow.service;

import jakarta.persistence.EntityManager;
import org.hibernate.LazyInitializationException;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.Quiz;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.QuizRepository;
import ru.mephi.learnflow.repository.QuizSubmissionRepository;
import ru.mephi.learnflow.repository.UserRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
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
    private QuizSubmissionRepository attempts;

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
    private EntityManager entities;

    private Long moduleId;
    private Long teacherId;
    private Long courseId;

    @BeforeEach
    void createCourseAndModule() {
        teacherId = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER)).getId();
        Long categoryId = categories.save(new Category("Базы данных")).getId();
        courseId = courses.createCourse("ORM", null, categoryId, teacherId).id();
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

    @Test
    void enrolledStudentSeesQuestionsAndOptionsInOrder() {
        Long studentId = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT)).getId();
        enrollments.enrollStudent(courseId, studentId);
        QuizDetails quiz = service.createQuiz(moduleId, teacherId, "Связи сущностей");
        QuizQuestionDetails relation = service.addQuestion(quiz.id(), teacherId, "Какая аннотация нужна?", List.of(
                new QuizOptionDraft("@OneToMany", false), new QuizOptionDraft("@ManyToOne", true)));
        QuizQuestionDetails fetch = service.addQuestion(quiz.id(), teacherId, "Как отложить загрузку?", List.of(
                new QuizOptionDraft("EAGER", false), new QuizOptionDraft("LAZY", true)));
        entities.clear();

        QuizView view = service.getQuizForStudent(quiz.id(), studentId);

        assertThat(view.quiz()).isEqualTo(quiz);
        assertThat(view.questions()).containsExactly(
                new QuizView.Question(relation.id(), relation.text(), List.of(
                        new QuizView.Option(relation.options().getFirst().id(), "@OneToMany"),
                        new QuizView.Option(relation.options().getLast().id(), "@ManyToOne"))),
                new QuizView.Question(fetch.id(), fetch.text(), List.of(
                        new QuizView.Option(fetch.options().getFirst().id(), "EAGER"),
                        new QuizView.Option(fetch.options().getLast().id(), "LAZY"))));
    }

    @Test
    void rejectsQuizViewAfterUnenrollment() {
        Long studentId = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT)).getId();
        QuizDetails quiz = service.createQuiz(moduleId, teacherId, "Связи сущностей");
        enrollments.enrollStudent(courseId, studentId);
        assertThat(service.getQuizForStudent(quiz.id(), studentId).questions()).isEmpty();

        enrollments.unenrollStudent(courseId, studentId);

        assertThatThrownBy(() -> service.getQuizForStudent(quiz.id(), studentId))
                .isInstanceOf(EnrollmentRequiredException.class);
    }

    @Test
    void savesEachQuizAttemptWithItsOwnScoreAndAnswers() {
        Long studentId = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT)).getId();
        enrollments.enrollStudent(courseId, studentId);
        Long quizId = service.createQuiz(moduleId, teacherId, "Связи сущностей").id();
        QuizQuestionDetails relation = service.addQuestion(quizId, teacherId, "Какая аннотация нужна?", List.of(
                new QuizOptionDraft("@OneToMany", false), new QuizOptionDraft("@ManyToOne", true)));
        QuizQuestionDetails fetch = service.addQuestion(quizId, teacherId, "Как отложить загрузку?", List.of(
                new QuizOptionDraft("EAGER", false), new QuizOptionDraft("LAZY", true)));
        Long wrongOption = relation.options().getFirst().id();
        Long rightOption = relation.options().getLast().id();
        Long lazyOption = fetch.options().getLast().id();
        entities.clear();

        QuizAttemptDetails first = service.submitQuiz(quizId, studentId, Map.of(relation.id(), wrongOption));
        QuizAttemptDetails second = service.submitQuiz(quizId, studentId,
                Map.of(relation.id(), rightOption, fetch.id(), lazyOption));
        entities.flush();
        entities.clear();

        assertThat(first.id()).isNotNull().isNotEqualTo(second.id());
        assertThat(first.takenAt()).isNotNull();
        assertThat(first.score()).isZero();
        assertThat(first.answers()).containsExactly(new QuizAttemptDetails.Answer(relation.id(), wrongOption));
        assertThat(second.studentId()).isEqualTo(studentId);
        assertThat(second.quizId()).isEqualTo(quizId);
        assertThat(second.score()).isEqualTo(2);
        assertThat(second.answers()).containsExactly(
                new QuizAttemptDetails.Answer(relation.id(), rightOption),
                new QuizAttemptDetails.Answer(fetch.id(), lazyOption));
        assertThat(attempts.findAllByQuiz_IdOrderByTakenAtDescIdDesc(quizId))
                .extracting(QuizAttemptDetails::from).containsExactly(second, first);
    }

    @Test
    void doesNotSaveAttemptWithoutEnrollmentOrWithUnknownAnswer() {
        Long studentId = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT)).getId();
        Long quizId = service.createQuiz(moduleId, teacherId, "Связи сущностей").id();
        QuizQuestionDetails question = service.addQuestion(quizId, teacherId, "Какая аннотация нужна?", List.of(
                new QuizOptionDraft("@OneToMany", false), new QuizOptionDraft("@ManyToOne", true)));

        Map<Long, Long> correctAnswer = Map.of(question.id(), question.options().getLast().id());
        assertThatThrownBy(() -> service.submitQuiz(quizId, studentId, correctAnswer))
                .isInstanceOf(EnrollmentRequiredException.class);
        enrollments.enrollStudent(courseId, studentId);
        assertThatIllegalArgumentException().isThrownBy(
                () -> service.submitQuiz(quizId, studentId, Map.of(question.id(), Long.MAX_VALUE)));
        assertThat(attempts.findAllByQuiz_IdOrderByTakenAtDescIdDesc(quizId)).isEmpty();
    }

    @Test
    void studentSeesOnlyOwnAttemptsAcrossQuizzesAfterUnenrollment() {
        Long studentId = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT)).getId();
        Long secondStudentId = users.save(new User("Иван", "ivan@example.org", UserRole.STUDENT)).getId();
        enrollments.enrollStudent(courseId, studentId);
        enrollments.enrollStudent(courseId, secondStudentId);
        Long secondModuleId = content.addModule(courseId, "Запросы").id();
        Long firstQuizId = service.createQuiz(moduleId, teacherId, "Связи сущностей").id();
        Long secondQuizId = service.createQuiz(secondModuleId, teacherId, "JPQL").id();
        QuizQuestionDetails relation = service.addQuestion(firstQuizId, teacherId, "Какая аннотация нужна?", List.of(
                new QuizOptionDraft("@OneToMany", false), new QuizOptionDraft("@ManyToOne", true)));
        QuizQuestionDetails query = service.addQuestion(secondQuizId, teacherId, "Как прочитать данные?", List.of(
                new QuizOptionDraft("DELETE", false), new QuizOptionDraft("SELECT", true)));
        assertThat(service.listStudentAttempts(studentId)).isEmpty();

        QuizAttemptDetails first = service.submitQuiz(firstQuizId, studentId,
                Map.of(relation.id(), relation.options().getFirst().id()));
        QuizAttemptDetails peer = service.submitQuiz(firstQuizId, secondStudentId,
                Map.of(relation.id(), relation.options().getLast().id()));
        QuizAttemptDetails last = service.submitQuiz(secondQuizId, studentId,
                Map.of(query.id(), query.options().getLast().id()));
        enrollments.unenrollStudent(courseId, studentId);
        entities.flush();
        entities.clear();

        assertThat(service.listStudentAttempts(studentId)).containsExactly(last, first);
        assertThat(service.listStudentAttempts(secondStudentId)).containsExactly(peer);
    }

    @Test
    void rejectsHistoryForNonStudentOrMissingUser() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.listStudentAttempts(teacherId));
        assertThatThrownBy(() -> service.listStudentAttempts(Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void teacherSeesAttemptsForSelectedQuizNewestFirst() {
        Long studentId = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT)).getId();
        Long peerId = users.save(new User("Иван", "ivan@example.org", UserRole.STUDENT)).getId();
        enrollments.enrollStudent(courseId, studentId);
        enrollments.enrollStudent(courseId, peerId);
        Long quizId = service.createQuiz(moduleId, teacherId, "Связи сущностей").id();
        Long otherModuleId = content.addModule(courseId, "Запросы").id();
        Long otherQuizId = service.createQuiz(otherModuleId, teacherId, "JPQL").id();
        QuizQuestionDetails question = service.addQuestion(quizId, teacherId, "Какая аннотация нужна?", List.of(
                new QuizOptionDraft("@OneToMany", false), new QuizOptionDraft("@ManyToOne", true)));
        assertThat(service.listQuizAttempts(quizId, teacherId)).isEmpty();

        QuizAttemptDetails first = service.submitQuiz(quizId, studentId,
                Map.of(question.id(), question.options().getFirst().id()));
        QuizAttemptDetails peer = service.submitQuiz(quizId, peerId,
                Map.of(question.id(), question.options().getLast().id()));
        QuizAttemptDetails retry = service.submitQuiz(quizId, studentId,
                Map.of(question.id(), question.options().getLast().id()));
        QuizAttemptDetails other = service.submitQuiz(otherQuizId, studentId, Map.of());
        entities.flush();
        entities.clear();

        assertThat(service.listQuizAttempts(quizId, teacherId)).containsExactly(retry, peer, first);
        assertThat(service.listQuizAttempts(otherQuizId, teacherId)).containsExactly(other);
    }

    @Test
    void rejectsQuizResultsForAnotherTeacherOrStudent() {
        Long quizId = service.createQuiz(moduleId, teacherId, "Связи сущностей").id();
        Long otherTeacherId = users.save(new User("Ирина", "irina@example.org", UserRole.TEACHER)).getId();
        Long studentId = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT)).getId();
        enrollments.enrollStudent(courseId, studentId);

        assertThatThrownBy(() -> service.listQuizAttempts(quizId, otherTeacherId))
                .isInstanceOf(CourseTeacherRequiredException.class);
        assertThatThrownBy(() -> service.listQuizAttempts(quizId, studentId))
                .isInstanceOf(CourseTeacherRequiredException.class);
    }

    @Test
    void serviceViewRemainsReadableAfterTransactionCloses() {
        Long studentId = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT)).getId();
        enrollments.enrollStudent(courseId, studentId);
        QuizDetails quiz = service.createQuiz(moduleId, teacherId, "Связи сущностей");
        QuizQuestionDetails question = service.addQuestion(quiz.id(), teacherId, "Какая аннотация нужна?", List.of(
                new QuizOptionDraft("@OneToMany", false), new QuizOptionDraft("@ManyToOne", true)));
        entities.clear();

        QuizView view = service.getQuizForStudent(quiz.id(), studentId);
        entities.clear();
        Quiz detached = quizzes.findById(quiz.id()).orElseThrow();
        assertThat(entities.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(detached, "questions")).isFalse();
        TestTransaction.end();

        assertThatExceptionOfType(LazyInitializationException.class)
                .isThrownBy(() -> detached.getQuestions().size());
        assertThat(view.quiz()).isEqualTo(quiz);
        assertThat(view.questions()).containsExactly(new QuizView.Question(question.id(), question.text(), List.of(
                new QuizView.Option(question.options().getFirst().id(), "@OneToMany"),
                new QuizView.Option(question.options().getLast().id(), "@ManyToOne"))));
    }

    @Test
    void loadsQuizAndAttemptListsWithBoundedSqlQueries() {
        Long studentId = users.save(new User("Анна", "anna@example.org", UserRole.STUDENT)).getId();
        enrollments.enrollStudent(courseId, studentId);
        Long quizId = service.createQuiz(moduleId, teacherId, "Связи сущностей").id();
        QuizQuestionDetails firstQuestion = service.addQuestion(quizId, teacherId, "Первый вопрос", List.of(
                new QuizOptionDraft("Первый вариант", true), new QuizOptionDraft("Второй вариант", false)));
        for (String question : List.of("Второй вопрос", "Третий вопрос", "Четвёртый вопрос")) {
            service.addQuestion(quizId, teacherId, question, List.of(
                    new QuizOptionDraft("Первый вариант", true), new QuizOptionDraft("Второй вариант", false)));
        }
        Map<Long, Long> answer = Map.of(firstQuestion.id(), firstQuestion.options().getFirst().id());
        service.submitQuiz(quizId, studentId, answer);
        service.submitQuiz(quizId, studentId, answer);
        service.submitQuiz(quizId, studentId, Map.of());
        entities.flush();

        Statistics statistics = entities.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean statisticsWereEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            long viewQueries = countStatements(statistics,
                    () -> assertThat(service.getQuizForStudent(quizId, studentId).questions()).hasSize(4));
            long studentQueries = countStatements(statistics,
                    () -> assertThat(service.listStudentAttempts(studentId)).hasSize(3));
            long teacherQueries = countStatements(statistics,
                    () -> assertThat(service.listQuizAttempts(quizId, teacherId)).hasSize(3));

            assertThat(List.of(viewQueries, studentQueries, teacherQueries))
                    .as("SQL для просмотра викторины, истории студента и результатов преподавателя")
                    .containsExactly(3L, 2L, 2L);
        } finally {
            statistics.setStatisticsEnabled(statisticsWereEnabled);
        }
    }

    private long countStatements(Statistics statistics, Runnable read) {
        entities.clear();
        long before = statistics.getPrepareStatementCount();
        read.run();
        return statistics.getPrepareStatementCount() - before;
    }
}
