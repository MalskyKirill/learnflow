package ru.mephi.learnflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.dto.CourseDetails;
import ru.mephi.learnflow.dto.CourseModuleDetails;
import ru.mephi.learnflow.dto.CourseProgram;
import ru.mephi.learnflow.dto.LessonDetails;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.CourseModule;
import ru.mephi.learnflow.exception.ResourceNotFoundException;
import ru.mephi.learnflow.repository.CourseModuleRepository;
import ru.mephi.learnflow.repository.CourseRepository;
import ru.mephi.learnflow.repository.LessonRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CourseContentService {

    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;

    public CourseContentService(CourseRepository courses, CourseModuleRepository modules, LessonRepository lessons) {
        this.courses = courses;
        this.modules = modules;
        this.lessons = lessons;
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
    public LessonDetails addLesson(Long moduleId, Long actorId, String title, String content) {
        CourseModule module = modules.findByIdForUpdate(moduleId)
                .orElseThrow(() -> moduleNotFound(moduleId));
        module.getCourse().requireTeacher(actorId);
        return LessonDetails.from(lessons.save(module.addLesson(title, content)));
    }

    public LessonDetails getLesson(Long lessonId) {
        return lessons.findById(lessonId).map(LessonDetails::from)
                .orElseThrow(() -> new ResourceNotFoundException("Урок " + lessonId + " не найден"));
    }

    public CourseProgram getProgram(Long courseId) {
        Course course = courses.findById(courseId).orElseThrow(() -> courseNotFound(courseId));
        List<CourseProgram.Module> program = modules.findAllWithLessonsByCourseId(courseId).stream()
                .map(module -> new CourseProgram.Module(CourseModuleDetails.from(module),
                        module.getLessons().stream().map(LessonDetails::from).toList()))
                .toList();
        return new CourseProgram(CourseDetails.from(course), program);
    }

    private ResourceNotFoundException courseNotFound(Long courseId) {
        return new ResourceNotFoundException("Курс " + courseId + " не найден");
    }

    private ResourceNotFoundException moduleNotFound(Long moduleId) {
        return new ResourceNotFoundException("Модуль " + moduleId + " не найден");
    }
}
