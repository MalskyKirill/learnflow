package ru.mephi.learnflow.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.Enrollment;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.repository.CourseRepository;
import ru.mephi.learnflow.repository.EnrollmentRepository;
import ru.mephi.learnflow.repository.UserRepository;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final UserRepository users;

    public EnrollmentService(EnrollmentRepository enrollments, CourseRepository courses, UserRepository users) {
        this.enrollments = enrollments;
        this.courses = courses;
        this.users = users;
    }

    @Transactional
    public EnrollmentDetails enrollStudent(Long courseId, Long studentId) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Курс " + courseId + " не найден"));
        User student = users.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь " + studentId + " не найден"));
        Enrollment enrollment = new Enrollment(student, course);
        try {
            return EnrollmentDetails.from(enrollments.saveAndFlush(enrollment));
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && Enrollment.UNIQUE_STUDENT_COURSE.equals(violation.getConstraintName())) {
                throw new AlreadyEnrolledException(studentId, courseId, exception);
            }
            throw exception;
        }
    }
}
