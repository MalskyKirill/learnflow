package ru.mephi.learnflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.dto.AssignmentDetails;
import ru.mephi.learnflow.entity.Assignment;
import ru.mephi.learnflow.entity.Lesson;
import ru.mephi.learnflow.exception.ResourceNotFoundException;
import ru.mephi.learnflow.repository.AssignmentRepository;
import ru.mephi.learnflow.repository.LessonRepository;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AssignmentService {

    private final AssignmentRepository assignments;
    private final LessonRepository lessons;

    public AssignmentService(AssignmentRepository assignments, LessonRepository lessons) {
        this.assignments = assignments;
        this.lessons = lessons;
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
                .orElseThrow(() -> new ResourceNotFoundException("Задание " + assignmentId + " не найдено"));
    }

    private ResourceNotFoundException lessonNotFound(Long lessonId) {
        return new ResourceNotFoundException("Урок " + lessonId + " не найден");
    }
}
