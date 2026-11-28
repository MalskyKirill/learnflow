package ru.mephi.learnflow.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.mephi.learnflow.dto.QuizOptionDraft;
import ru.mephi.learnflow.repository.CourseRepository;
import ru.mephi.learnflow.service.AssignmentService;
import ru.mephi.learnflow.service.CourseContentService;
import ru.mephi.learnflow.service.CourseService;
import ru.mephi.learnflow.service.EnrollmentService;
import ru.mephi.learnflow.service.QuizService;

import java.util.List;

@Component
@Profile("demo")
class DemoCourseInitializer {

    static final String COURSE_TITLE = "Практика ORM с Hibernate";

    private final CourseRepository courses;
    private final CourseService courseService;
    private final CourseContentService content;
    private final AssignmentService assignments;
    private final QuizService quizzes;
    private final EnrollmentService enrollments;

    DemoCourseInitializer(CourseRepository courses, CourseService courseService, CourseContentService content,
            AssignmentService assignments, QuizService quizzes, EnrollmentService enrollments) {
        this.courses = courses;
        this.courseService = courseService;
        this.content = content;
        this.assignments = assignments;
        this.quizzes = quizzes;
        this.enrollments = enrollments;
    }

    void initialize(Long categoryId, Long teacherId, Long studentId) {
        if (courses.existsByTeacher_IdAndTitle(teacherId, COURSE_TITLE)) {
            return;
        }

        Long courseId = courseService.createCourse(COURSE_TITLE,
                "Смоделируем связи между сущностями и проверим, когда Hibernate загружает данные.",
                categoryId, teacherId).id();
        Long moduleId = content.addModule(courseId, teacherId, "Связи и загрузка").id();
        Long lessonId = content.addLesson(moduleId, teacherId, "Сущности и связи",
                "В LearnFlow урок принадлежит модулю. Разберись, где хранится внешний ключ и какая сторона владеет связью.")
                .id();
        assignments.createAssignment(lessonId, teacherId, "Связь урока с модулем",
                "Опиши связь между уроком и модулем. Укажи аннотацию JPA и таблицу с внешним ключом.", null, 20);

        Long quizId = quizzes.createQuiz(moduleId, teacherId, "Связи JPA и ленивая загрузка").id();
        quizzes.addQuestion(quizId, teacherId, "Какая аннотация описывает связь урока с модулем?", List.of(
                new QuizOptionDraft("@ManyToOne", true),
                new QuizOptionDraft("@OneToMany", false),
                new QuizOptionDraft("@OneToOne", false)));
        quizzes.addQuestion(quizId, teacherId, "Какой режим откладывает загрузку связи?", List.of(
                new QuizOptionDraft("LAZY", true),
                new QuizOptionDraft("EAGER", false)));
        enrollments.enrollStudent(courseId, studentId);
    }
}
