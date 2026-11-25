package ru.mephi.learnflow.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.learnflow.entity.Submission;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    boolean existsByStudent_IdAndAssignment_Id(Long studentId, Long assignmentId);

    List<Submission> findAllByAssignment_Id(Long assignmentId);

    List<Submission> findAllByStudent_Id(Long studentId);

    List<Submission> findAllByAssignment_IdOrderBySubmittedAtDescIdDesc(Long assignmentId);

    List<Submission> findAllByStudent_IdOrderBySubmittedAtDescIdDesc(Long studentId);

    @EntityGraph(attributePaths = "assignment.lesson.module.course")
    Optional<Submission> findWithCourseById(Long id);
}
