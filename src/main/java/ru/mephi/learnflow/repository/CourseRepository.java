package ru.mephi.learnflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.learnflow.entity.Course;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findAllByCategory_Id(Long categoryId);

    @Query("""
            select case when count(c) > 0 then true else false end
            from Course c where c.id = :courseId and (
                exists (select e.id from Enrollment e where e.course.id = c.id)
                or exists (select r.id from CourseReview r where r.course.id = c.id)
                or exists (select q.id from Quiz q where q.module.course.id = c.id)
                or exists (select s.id from Submission s where s.assignment.lesson.module.course.id = c.id)
            )
            """)
    boolean hasDependentData(@Param("courseId") Long courseId);
}
