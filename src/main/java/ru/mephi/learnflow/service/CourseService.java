package ru.mephi.learnflow.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.CourseRepository;
import ru.mephi.learnflow.repository.UserRepository;

import java.sql.SQLException;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courses;
    private final CategoryRepository categories;
    private final UserRepository users;

    public CourseService(CourseRepository courses, CategoryRepository categories, UserRepository users) {
        this.courses = courses;
        this.categories = categories;
        this.users = users;
    }

    @Transactional
    public CourseDetails createCourse(String title, String description, Long categoryId, Long teacherId) {
        Course course = new Course(title, description, requireCategory(categoryId), requireTeacher(teacherId));
        return CourseDetails.from(courses.save(course));
    }

    public CourseDetails getCourse(Long courseId) {
        return CourseDetails.from(requireCourse(courseId));
    }

    public List<CourseDetails> listCourses() {
        return courses.findAll(Sort.by("id")).stream().map(CourseDetails::from).toList();
    }

    @Transactional
    public CourseDetails updateCourse(Long courseId, String title, String description, Long categoryId, Long teacherId) {
        Course course = requireCourse(courseId);
        course.updateDetails(title, description, requireCategory(categoryId), requireTeacher(teacherId));
        return CourseDetails.from(course);
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        Course course = requireCourse(courseId);
        if (courses.hasDependentData(courseId)) {
            throw new CourseInUseException(courseId);
        }
        try {
            courses.delete(course);
            courses.flush();
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMostSpecificCause() instanceof SQLException sqlException
                    && "23503".equals(sqlException.getSQLState())) {
                throw new CourseInUseException(courseId, exception);
            }
            throw exception;
        }
    }

    private Course requireCourse(Long courseId) {
        return courses.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Курс " + courseId + " не найден"));
    }

    private Category requireCategory(Long categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Категория " + categoryId + " не найдена"));
    }

    private User requireTeacher(Long teacherId) {
        return users.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь " + teacherId + " не найден"));
    }
}
