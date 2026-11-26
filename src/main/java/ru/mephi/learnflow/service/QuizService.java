package ru.mephi.learnflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.CourseModule;
import ru.mephi.learnflow.entity.Question;
import ru.mephi.learnflow.entity.Quiz;
import ru.mephi.learnflow.repository.CourseModuleRepository;
import ru.mephi.learnflow.repository.EnrollmentRepository;
import ru.mephi.learnflow.repository.QuizRepository;

import java.util.List;

@Service
public class QuizService {

    private final QuizRepository quizzes;
    private final CourseModuleRepository modules;
    private final EnrollmentRepository enrollments;

    public QuizService(QuizRepository quizzes, CourseModuleRepository modules, EnrollmentRepository enrollments) {
        this.quizzes = quizzes;
        this.modules = modules;
        this.enrollments = enrollments;
    }

    @Transactional
    public QuizDetails createQuiz(Long moduleId, Long teacherId, String title) {
        CourseModule module = modules.findByIdForUpdate(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Модуль " + moduleId + " не найден"));
        requireCourseTeacher(module.getCourse(), teacherId);
        if (quizzes.existsByModule_Id(moduleId)) {
            throw new QuizAlreadyExistsException(moduleId);
        }
        return QuizDetails.from(quizzes.save(new Quiz(module, title)));
    }

    @Transactional
    public QuizQuestionDetails addQuestion(Long quizId, Long teacherId, String text, List<QuizOptionDraft> options) {
        Quiz quiz = requireQuiz(quizId);
        requireCourseTeacher(quiz.getModule().getCourse(), teacherId);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Текст вопроса не должен быть пустым");
        }
        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException("У вопроса должно быть не менее двух вариантов ответа");
        }
        if (options.stream().anyMatch(option -> option == null || option.text() == null || option.text().isBlank())) {
            throw new IllegalArgumentException("Текст варианта ответа не должен быть пустым");
        }
        if (options.stream().filter(QuizOptionDraft::correct).count() != 1) {
            throw new IllegalArgumentException("У вопроса должен быть ровно один правильный вариант");
        }

        Question question = quiz.addQuestion(text);
        options.forEach(option -> question.addOption(option.text(), option.correct()));
        quizzes.flush();
        return QuizQuestionDetails.from(question);
    }

    @Transactional(readOnly = true)
    public QuizView getQuizForStudent(Long quizId, Long studentId) {
        Quiz quiz = requireQuiz(quizId);
        Long courseId = quiz.getModule().getCourse().getId();
        if (enrollments.findByStudent_IdAndCourse_Id(studentId, courseId).isEmpty()) {
            throw new EnrollmentRequiredException(courseId, "просмотра викторины");
        }
        return QuizView.from(quiz);
    }

    private Quiz requireQuiz(Long quizId) {
        return quizzes.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Викторина " + quizId + " не найдена"));
    }

    private void requireCourseTeacher(Course course, Long teacherId) {
        if (!course.getTeacher().getId().equals(teacherId)) {
            throw new CourseTeacherRequiredException(course.getId());
        }
    }
}
