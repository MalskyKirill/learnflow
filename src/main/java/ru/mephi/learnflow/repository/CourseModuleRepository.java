package ru.mephi.learnflow.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.learnflow.entity.CourseModule;

import java.util.List;
import java.util.Optional;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {
    List<CourseModule> findAllByCourse_IdOrderByPositionAsc(Long courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CourseModule m where m.id = :moduleId")
    Optional<CourseModule> findByIdForUpdate(@Param("moduleId") Long moduleId);

    @Query("""
            select m from CourseModule m left join fetch m.lessons
            where m.course.id = :courseId order by m.position
            """)
    List<CourseModule> findAllWithLessonsByCourseId(@Param("courseId") Long courseId);
}
