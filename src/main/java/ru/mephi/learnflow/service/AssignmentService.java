package ru.mephi.learnflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.dto.AssignmentDetails;
import ru.mephi.learnflow.entity.Assignment;
import ru.mephi.learnflow.entity.Lesson;
import ru.mephi.learnflow.exception.CourseContentInUseException;
import ru.mephi.learnflow.exception.ResourceNotFoundException;
import ru.mephi.learnflow.repository.AssignmentRepository;
import ru.mephi.learnflow.repository.LessonRepository;
import ru.mephi.learnflow.repository.SubmissionRepository;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AssignmentService {

    private final AssignmentRepository assignments;
    private final LessonRepository lessons;
    private final SubmissionRepository submissions;

    public AssignmentService(AssignmentRepository assignments, LessonRepository lessons,
                             SubmissionRepository submissions) {
        this.assignments = assignments;
        this.lessons = lessons;
        this.submissions = submissions;
    }

    @Transactional
    public AssignmentDetails createAssignment(Long lessonId, Long teacherId, String title, String description,
                                              Instant dueAt, int maxScore) {
        Lesson lesson = lessons.findWithCourseById(lessonId)
                .orElseThrow(() -> lessonNotFound(lessonId));
        lesson.getModule().getCourse().requireTeacher(teacherId);
        Assignment assignment = lesson.addAssignment(title, description, dueAt, maxScore);
        return AssignmentDetails.from(assignments.save(assignment));
    }

    public List<AssignmentDetails> listAssignments(Long lessonId) {
        if (!lessons.existsById(lessonId)) {
            throw lessonNotFound(lessonId);
        }
        return assignments.findAllByLesson_IdOrderByIdAsc(lessonId).stream()
                .map(AssignmentDetails::from)
                .toList();
    }

    public AssignmentDetails getAssignment(Long assignmentId) {
        return assignments.findById(assignmentId).map(AssignmentDetails::from)
                .orElseThrow(() -> assignmentNotFound(assignmentId));
    }

    @Transactional
    public AssignmentDetails updateAssignment(Long assignmentId, Long teacherId, String title, String description,
                                              Instant dueAt, int maxScore) {
        Assignment assignment = requireOwnedAssignmentForUpdate(assignmentId, teacherId);
        if (maxScore > 0 && maxScore < assignment.getMaxScore()
                && submissions.existsByAssignment_IdAndScoreGreaterThan(assignmentId, maxScore)) {
            throw new CourseContentInUseException("Максимальный балл задания не может быть ниже выставленной оценки");
        }
        assignment.updateDetails(title, description, dueAt, maxScore);
        return AssignmentDetails.from(assignment);
    }

    @Transactional
    public void deleteAssignment(Long assignmentId, Long teacherId) {
        Assignment assignment = requireOwnedAssignmentForUpdate(assignmentId, teacherId);
        if (submissions.existsByAssignment_Id(assignmentId)) {
            throw new CourseContentInUseException("Нельзя удалить задание " + assignmentId + ", пока у него есть решения");
        }
        assignment.getLesson().removeAssignment(assignment);
        assignments.flush();
    }

    private Assignment requireOwnedAssignmentForUpdate(Long assignmentId, Long teacherId) {
        Assignment assignment = assignments.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> assignmentNotFound(assignmentId));
        assignment.getLesson().getModule().getCourse().requireTeacher(teacherId);
        return assignment;
    }

    private ResourceNotFoundException assignmentNotFound(Long assignmentId) {
        return new ResourceNotFoundException("Задание " + assignmentId + " не найдено");
    }

    private ResourceNotFoundException lessonNotFound(Long lessonId) {
        return new ResourceNotFoundException("Урок " + lessonId + " не найден");
    }
}
