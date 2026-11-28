package ru.mephi.learnflow.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.dto.CourseDetails;
import ru.mephi.learnflow.dto.CourseModuleDetails;
import ru.mephi.learnflow.dto.CourseProgram;
import ru.mephi.learnflow.dto.LessonDetails;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.CourseModule;
import ru.mephi.learnflow.entity.Lesson;
import ru.mephi.learnflow.exception.CourseContentInUseException;
import ru.mephi.learnflow.exception.ResourceNotFoundException;
import ru.mephi.learnflow.repository.CourseModuleRepository;
import ru.mephi.learnflow.repository.CourseRepository;
import ru.mephi.learnflow.repository.LessonRepository;
import ru.mephi.learnflow.repository.QuizRepository;
import ru.mephi.learnflow.repository.SubmissionRepository;

import java.sql.SQLException;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CourseContentService {

    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final QuizRepository quizzes;
    private final SubmissionRepository submissions;

    public CourseContentService(CourseRepository courses, CourseModuleRepository modules, LessonRepository lessons,
                                QuizRepository quizzes, SubmissionRepository submissions) {
        this.courses = courses;
        this.modules = modules;
        this.lessons = lessons;
        this.quizzes = quizzes;
        this.submissions = submissions;
    }

    @Transactional
    public CourseModuleDetails addModule(Long courseId, Long actorId, String title) {
        Course course = courses.findByIdForUpdate(courseId)
                .orElseThrow(() -> courseNotFound(courseId));
        course.requireTeacher(actorId);
        return CourseModuleDetails.from(modules.save(course.addModule(title)));
    }

    public List<CourseModuleDetails> listModules(Long courseId) {
        if (!courses.existsById(courseId)) {
            throw courseNotFound(courseId);
        }
        return modules.findAllByCourse_IdOrderByPositionAsc(courseId).stream()
                .map(CourseModuleDetails::from)
                .toList();
    }

    public CourseModuleDetails getModule(Long moduleId) {
        return modules.findById(moduleId).map(CourseModuleDetails::from)
                .orElseThrow(() -> moduleNotFound(moduleId));
    }

    @Transactional
    public CourseModuleDetails updateModule(Long moduleId, Long actorId, String title) {
        CourseModule module = requireModuleForUpdate(moduleId, actorId);
        module.rename(title);
        return CourseModuleDetails.from(module);
    }

    @Transactional
    public void deleteModule(Long moduleId, Long actorId) {
        CourseModule module = requireModuleForUpdate(moduleId, actorId);
        String conflict = "Нельзя удалить модуль " + moduleId + ", пока у него есть викторина или решения заданий";
        if (quizzes.existsByModule_Id(moduleId) || submissions.existsByAssignment_Lesson_Module_Id(moduleId)) {
            throw new CourseContentInUseException(conflict);
        }
        module.getCourse().removeModule(module);
        flushDeletion(modules::flush, conflict);
    }

    @Transactional
    public LessonDetails addLesson(Long moduleId, Long actorId, String title, String content) {
        CourseModule module = requireModuleForUpdate(moduleId, actorId);
        return LessonDetails.from(lessons.save(module.addLesson(title, content)));
    }

    public LessonDetails getLesson(Long lessonId) {
        return lessons.findById(lessonId).map(LessonDetails::from)
                .orElseThrow(() -> lessonNotFound(lessonId));
    }

    @Transactional
    public LessonDetails updateLesson(Long lessonId, Long actorId, String title, String content) {
        Lesson lesson = requireLessonForUpdate(lessonId, actorId);
        lesson.updateDetails(title, content);
        return LessonDetails.from(lesson);
    }

    @Transactional
    public void deleteLesson(Long lessonId, Long actorId) {
        Lesson lesson = requireLessonForUpdate(lessonId, actorId);
        String conflict = "Нельзя удалить урок " + lessonId + ", пока к его заданиям есть решения";
        if (submissions.existsByAssignment_Lesson_Id(lessonId)) {
            throw new CourseContentInUseException(conflict);
        }
        lesson.getModule().removeLesson(lesson);
        flushDeletion(lessons::flush, conflict);
    }

    public CourseProgram getProgram(Long courseId) {
        Course course = courses.findById(courseId).orElseThrow(() -> courseNotFound(courseId));
        List<CourseProgram.Module> program = modules.findAllWithLessonsByCourseId(courseId).stream()
                .map(module -> new CourseProgram.Module(CourseModuleDetails.from(module),
                        module.getLessons().stream().map(LessonDetails::from).toList()))
                .toList();
        return new CourseProgram(CourseDetails.from(course), program);
    }

    private CourseModule requireModuleForUpdate(Long moduleId, Long actorId) {
        CourseModule module = modules.findByIdForUpdate(moduleId).orElseThrow(() -> moduleNotFound(moduleId));
        module.getCourse().requireTeacher(actorId);
        return module;
    }

    private Lesson requireLessonForUpdate(Long lessonId, Long actorId) {
        Lesson lesson = lessons.findByIdForUpdate(lessonId).orElseThrow(() -> lessonNotFound(lessonId));
        lesson.getModule().getCourse().requireTeacher(actorId);
        return lesson;
    }

    private void flushDeletion(Runnable flush, String conflict) {
        try {
            flush.run();
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMostSpecificCause() instanceof SQLException sqlException
                    && "23503".equals(sqlException.getSQLState())) {
                throw new CourseContentInUseException(conflict, exception);
            }
            throw exception;
        }
    }

    private ResourceNotFoundException courseNotFound(Long courseId) {
        return new ResourceNotFoundException("Курс " + courseId + " не найден");
    }

    private ResourceNotFoundException moduleNotFound(Long moduleId) {
        return new ResourceNotFoundException("Модуль " + moduleId + " не найден");
    }

    private ResourceNotFoundException lessonNotFound(Long lessonId) {
        return new ResourceNotFoundException("Урок " + lessonId + " не найден");
    }
}
