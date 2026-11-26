package ru.mephi.learnflow.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.dto.SubmissionDetails;
import ru.mephi.learnflow.entity.Assignment;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.Enrollment;
import ru.mephi.learnflow.entity.Submission;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.exception.AlreadySubmittedException;
import ru.mephi.learnflow.exception.CourseTeacherRequiredException;
import ru.mephi.learnflow.exception.EnrollmentRequiredException;
import ru.mephi.learnflow.exception.ResourceNotFoundException;
import ru.mephi.learnflow.repository.AssignmentRepository;
import ru.mephi.learnflow.repository.EnrollmentRepository;
import ru.mephi.learnflow.repository.SubmissionRepository;
import ru.mephi.learnflow.repository.UserRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SubmissionService {

    private final AssignmentRepository assignments;
    private final EnrollmentRepository enrollments;
    private final SubmissionRepository submissions;
    private final UserRepository users;

    public SubmissionService(AssignmentRepository assignments, EnrollmentRepository enrollments,
                             SubmissionRepository submissions, UserRepository users) {
        this.assignments = assignments;
        this.enrollments = enrollments;
        this.submissions = submissions;
        this.users = users;
    }

    @Transactional
    public SubmissionDetails submitAssignment(Long assignmentId, Long studentId, String content) {
        Assignment assignment = requireAssignment(assignmentId);
        Long courseId = assignment.getLesson().getModule().getCourse().getId();
        Enrollment enrollment = enrollments.findByStudentAndCourseForUpdate(studentId, courseId)
                .orElseThrow(() -> new EnrollmentRequiredException(courseId));
        Submission submission = assignment.addSubmission(enrollment.getStudent(), content);
        try {
            return SubmissionDetails.from(submissions.saveAndFlush(submission));
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && Submission.UNIQUE_STUDENT_ASSIGNMENT.equals(violation.getConstraintName())) {
                throw new AlreadySubmittedException(assignmentId, exception);
            }
            throw exception;
        }
    }

    @Transactional
    public SubmissionDetails gradeSubmission(Long submissionId, Long teacherId, int score, String feedback) {
        Submission submission = submissions.findWithCourseById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Решение " + submissionId + " не найдено"));
        requireCourseTeacher(submission.getAssignment(), teacherId);
        submission.grade(score, feedback);
        return SubmissionDetails.from(submission);
    }

    public List<SubmissionDetails> listAssignmentSubmissions(Long assignmentId, Long teacherId) {
        requireCourseTeacher(requireAssignment(assignmentId), teacherId);
        return submissions.findAllByAssignment_IdOrderBySubmittedAtDescIdDesc(assignmentId).stream()
                .map(SubmissionDetails::from)
                .toList();
    }

    public List<SubmissionDetails> listStudentSubmissions(Long studentId) {
        User student = users.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь " + studentId + " не найден"));
        if (student.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException("Просматривать свои решения может только студент");
        }
        return submissions.findAllByStudent_IdOrderBySubmittedAtDescIdDesc(studentId).stream()
                .map(SubmissionDetails::from)
                .toList();
    }

    private Assignment requireAssignment(Long assignmentId) {
        return assignments.findWithLessonAndModuleById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Задание " + assignmentId + " не найдено"));
    }

    private void requireCourseTeacher(Assignment assignment, Long teacherId) {
        Course course = assignment.getLesson().getModule().getCourse();
        if (!course.getTeacher().getId().equals(teacherId)) {
            throw new CourseTeacherRequiredException(course.getId());
        }
    }
}
