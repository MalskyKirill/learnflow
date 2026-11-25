package ru.mephi.learnflow.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.learnflow.entity.Course;
import ru.mephi.learnflow.entity.Enrollment;
import ru.mephi.learnflow.entity.User;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    Optional<Enrollment> findByStudent_IdAndCourse_Id(Long studentId, Long courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Enrollment e where e.student.id = :studentId and e.course.id = :courseId")
    Optional<Enrollment> findByStudentAndCourseForUpdate(@Param("studentId") Long studentId,
                                                        @Param("courseId") Long courseId);

    List<Enrollment> findAllByStudent_Id(Long studentId);

    List<Enrollment> findAllByCourse_Id(Long courseId);

    @Query("select e.course from Enrollment e where e.student.id = :studentId order by e.id")
    List<Course> findCoursesByStudentId(@Param("studentId") Long studentId);

    @Query("select e.student from Enrollment e where e.course.id = :courseId order by e.id")
    List<User> findStudentsByCourseId(@Param("courseId") Long courseId);
}
