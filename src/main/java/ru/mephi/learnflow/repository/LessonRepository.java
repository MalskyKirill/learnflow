package ru.mephi.learnflow.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.learnflow.entity.Lesson;

import java.util.List;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findAllByModule_IdOrderByPositionAsc(Long moduleId);

    @EntityGraph(attributePaths = "module.course")
    Optional<Lesson> findWithCourseById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lesson l where l.id = :lessonId")
    Optional<Lesson> findByIdForUpdate(@Param("lessonId") Long lessonId);
}
