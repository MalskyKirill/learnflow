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

import java.util.List;

@Service
@Transactional(readOnly = true)
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
        Course course = courses.findById(courseId).orElseThrow(() -> courseNotFound(courseId));
        User student = users.findById(studentId).orElseThrow(() -> studentNotFound(studentId));
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

    @Transactional
    public void unenrollStudent(Long courseId, Long studentId) {
        enrollments.findByStudentAndCourseForUpdate(studentId, courseId).ifPresent(enrollments::delete);
    }

    public List<StudentDetails> listStudents(Long courseId) {
        if (!courses.existsById(courseId)) {
            throw courseNotFound(courseId);
        }
        return enrollments.findStudentsByCourseId(courseId).stream().map(StudentDetails::from).toList();
    }

    public List<CourseDetails> listCourses(Long studentId) {
        if (!users.existsById(studentId)) {
            throw studentNotFound(studentId);
        }
        return enrollments.findCoursesByStudentId(studentId).stream().map(CourseDetails::from).toList();
    }

    private ResourceNotFoundException courseNotFound(Long courseId) {
        return new ResourceNotFoundException("Курс " + courseId + " не найден");
    }

    private ResourceNotFoundException studentNotFound(Long studentId) {
        return new ResourceNotFoundException("Пользователь " + studentId + " не найден");
    }
}
