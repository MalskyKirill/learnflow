package ru.mephi.learnflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.repository.CourseModuleRepository;
import ru.mephi.learnflow.repository.CourseRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CourseContentService {

    private final CourseRepository courses;
    private final CourseModuleRepository modules;

    public CourseContentService(CourseRepository courses, CourseModuleRepository modules) {
        this.courses = courses;
        this.modules = modules;
    }

    @Transactional
    public CourseModuleDetails addModule(Long courseId, String title) {
        Course course = courses.findByIdForUpdate(courseId)
                .orElseThrow(() -> courseNotFound(courseId));
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

    private ResourceNotFoundException courseNotFound(Long courseId) {
        return new ResourceNotFoundException("Курс " + courseId + " не найден");
    }
}
