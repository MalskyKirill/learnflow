package ru.mephi.learnflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.CourseModule;
import ru.mephi.learnflow.entity.Quiz;
import ru.mephi.learnflow.repository.CourseModuleRepository;
import ru.mephi.learnflow.repository.QuizRepository;

@Service
public class QuizService {

    private final QuizRepository quizzes;
    private final CourseModuleRepository modules;

    public QuizService(QuizRepository quizzes, CourseModuleRepository modules) {
        this.quizzes = quizzes;
        this.modules = modules;
    }

    @Transactional
    public QuizDetails createQuiz(Long moduleId, Long teacherId, String title) {
        CourseModule module = modules.findByIdForUpdate(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Модуль " + moduleId + " не найден"));
        Course course = module.getCourse();
        if (!course.getTeacher().getId().equals(teacherId)) {
            throw new CourseTeacherRequiredException(course.getId());
        }
        if (quizzes.existsByModule_Id(moduleId)) {
            throw new QuizAlreadyExistsException(moduleId);
        }
        return QuizDetails.from(quizzes.save(new Quiz(module, title)));
    }
}
