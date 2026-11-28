package ru.mephi.learnflow.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.learnflow.entity.Assignment;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findAllByLesson_IdOrderByIdAsc(Long lessonId);

    @EntityGraph(attributePaths = "lesson.module")
    Optional<Assignment> findWithLessonAndModuleById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :assignmentId")
    Optional<Assignment> findByIdForUpdate(@Param("assignmentId") Long assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from Assignment a
            where a.id = (select s.assignment.id from Submission s where s.id = :submissionId)
            """)
    Optional<Assignment> findBySubmissionIdForUpdate(@Param("submissionId") Long submissionId);
}
