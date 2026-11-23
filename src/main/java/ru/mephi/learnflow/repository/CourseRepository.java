package ru.mephi.learnflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.learnflow.entity.Course;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findAllByCategory_Id(Long categoryId);
}
