package ru.mephi.learnflow.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Assignment;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.Enrollment;
import ru.mephi.learnflow.entity.Submission;
import ru.mephi.learnflow.repository.AssignmentRepository;
import ru.mephi.learnflow.repository.EnrollmentRepository;
import ru.mephi.learnflow.repository.SubmissionRepository;

@Service
public class SubmissionService {

    private final AssignmentRepository assignments;
    private final EnrollmentRepository enrollments;
    private final SubmissionRepository submissions;

    public SubmissionService(AssignmentRepository assignments, EnrollmentRepository enrollments,
                             SubmissionRepository submissions) {
        this.assignments = assignments;
        this.enrollments = enrollments;
        this.submissions = submissions;
    }

    @Transactional
    public SubmissionDetails submitAssignment(Long assignmentId, Long studentId, String content) {
        Assignment assignment = assignments.findWithLessonAndModuleById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Задание " + assignmentId + " не найдено"));
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
        Course course = submission.getAssignment().getLesson().getModule().getCourse();
        if (!course.getTeacher().getId().equals(teacherId)) {
            throw new CourseTeacherRequiredException(course.getId());
        }
        submission.grade(score, feedback);
        return SubmissionDetails.from(submission);
    }
}
