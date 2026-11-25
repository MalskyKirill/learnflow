package ru.mephi.learnflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.learnflow.entity.Assignment;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.Lesson;
import ru.mephi.learnflow.repository.AssignmentRepository;
import ru.mephi.learnflow.repository.LessonRepository;

import java.time.Instant;

@Service
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
                .orElseThrow(() -> new ResourceNotFoundException("Урок " + lessonId + " не найден"));
        Course course = lesson.getModule().getCourse();
        if (!course.getTeacher().getId().equals(teacherId)) {
            throw new CourseTeacherRequiredException(course.getId());
        }
        Assignment assignment = lesson.addAssignment(title, description, dueAt, maxScore);
        return AssignmentDetails.from(assignments.save(assignment));
    }
}
