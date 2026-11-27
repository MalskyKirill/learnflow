package ru.mephi.learnflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.dto.QuizAttemptDetails;
import ru.mephi.learnflow.dto.QuizDetails;
import ru.mephi.learnflow.dto.QuizOptionDraft;
import ru.mephi.learnflow.dto.QuizQuestionDetails;
import ru.mephi.learnflow.dto.QuizView;
import ru.mephi.learnflow.entity.CourseModule;
import ru.mephi.learnflow.entity.Enrollment;
import ru.mephi.learnflow.entity.Question;
import ru.mephi.learnflow.entity.Quiz;
import ru.mephi.learnflow.entity.QuizSubmission;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.exception.EnrollmentRequiredException;
import ru.mephi.learnflow.exception.InvalidInputException;
import ru.mephi.learnflow.exception.QuizAlreadyExistsException;
import ru.mephi.learnflow.exception.ResourceNotFoundException;
import ru.mephi.learnflow.repository.CourseModuleRepository;
import ru.mephi.learnflow.repository.EnrollmentRepository;
import ru.mephi.learnflow.repository.QuizRepository;
import ru.mephi.learnflow.repository.QuizSubmissionRepository;
import ru.mephi.learnflow.repository.UserRepository;

import java.util.List;
import java.util.Map;

@Service
public class QuizService {

    private final QuizRepository quizzes;
    private final CourseModuleRepository modules;
    private final EnrollmentRepository enrollments;
    private final QuizSubmissionRepository attempts;
    private final UserRepository users;

    public QuizService(QuizRepository quizzes, CourseModuleRepository modules, EnrollmentRepository enrollments,
                       QuizSubmissionRepository attempts, UserRepository users) {
        this.quizzes = quizzes;
        this.modules = modules;
        this.enrollments = enrollments;
        this.attempts = attempts;
        this.users = users;
    }

    @Transactional
    public QuizDetails createQuiz(Long moduleId, Long teacherId, String title) {
        CourseModule module = modules.findByIdForUpdate(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Модуль " + moduleId + " не найден"));
        module.getCourse().requireTeacher(teacherId);
        if (quizzes.existsByModule_Id(moduleId)) {
            throw new QuizAlreadyExistsException(moduleId);
        }
        return QuizDetails.from(quizzes.save(new Quiz(module, title)));
    }

    @Transactional
    public QuizQuestionDetails addQuestion(Long quizId, Long teacherId, String text, List<QuizOptionDraft> options) {
        Quiz quiz = requireQuiz(quizId);
        quiz.getModule().getCourse().requireTeacher(teacherId);
        if (text == null || text.isBlank()) {
            throw new InvalidInputException("Текст вопроса не должен быть пустым");
        }
        if (options == null || options.size() < 2) {
            throw new InvalidInputException("У вопроса должно быть не менее двух вариантов ответа");
        }
        if (options.stream().anyMatch(option -> option == null || option.text() == null || option.text().isBlank())) {
            throw new InvalidInputException("Текст варианта ответа не должен быть пустым");
        }
        if (options.stream().filter(QuizOptionDraft::correct).count() != 1) {
            throw new InvalidInputException("У вопроса должен быть ровно один правильный вариант");
        }

        Question question = quiz.addQuestion(text);
        options.forEach(option -> question.addOption(option.text(), option.correct()));
        quizzes.flush();
        return QuizQuestionDetails.from(question);
    }

    @Transactional(readOnly = true)
    public QuizView getQuizForStudent(Long quizId, Long studentId) {
        Quiz quiz = requireQuizWithQuestions(quizId);
        Long courseId = quiz.getModule().getCourse().getId();
        if (enrollments.findByStudent_IdAndCourse_Id(studentId, courseId).isEmpty()) {
            throw new EnrollmentRequiredException(courseId, "просмотра викторины");
        }
        quizzes.findQuestionsWithOptionsByQuizId(quizId);
        return QuizView.from(quiz);
    }

    @Transactional
    public QuizAttemptDetails submitQuiz(Long quizId, Long studentId, Map<Long, Long> answers) {
        Quiz quiz = requireQuizWithQuestions(quizId);
        Long courseId = quiz.getModule().getCourse().getId();
        Enrollment enrollment = enrollments.findByStudentAndCourseForUpdate(studentId, courseId)
                .orElseThrow(() -> new EnrollmentRequiredException(courseId, "прохождения викторины"));
        quizzes.findQuestionsWithOptionsByQuizId(quizId);
        QuizSubmission attempt = quiz.gradeAttempt(enrollment.getStudent(), answers);
        return QuizAttemptDetails.from(attempts.save(attempt));
    }

    @Transactional(readOnly = true)
    public List<QuizAttemptDetails> listStudentAttempts(Long studentId) {
        User student = users.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь " + studentId + " не найден"));
        if (student.getRole() != UserRole.STUDENT) {
            throw new InvalidInputException("Просматривать историю своих попыток может только студент");
        }
        return attempts.findAllByStudent_IdOrderByTakenAtDescIdDesc(studentId).stream()
                .map(QuizAttemptDetails::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuizAttemptDetails> listQuizAttempts(Long quizId, Long teacherId) {
        Quiz quiz = requireQuiz(quizId);
        quiz.getModule().getCourse().requireTeacher(teacherId);
        return attempts.findAllByQuiz_IdOrderByTakenAtDescIdDesc(quizId).stream()
                .map(QuizAttemptDetails::from)
                .toList();
    }

    private Quiz requireQuiz(Long quizId) {
        return quizzes.findWithCourseById(quizId).orElseThrow(() -> quizNotFound(quizId));
    }

    private Quiz requireQuizWithQuestions(Long quizId) {
        return quizzes.findWithQuestionsById(quizId).orElseThrow(() -> quizNotFound(quizId));
    }

    private ResourceNotFoundException quizNotFound(Long quizId) {
        return new ResourceNotFoundException("Викторина " + quizId + " не найдена");
    }
}
