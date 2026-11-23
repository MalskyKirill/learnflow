package ru.mephi.learnflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.learnflow.entity.Assignment;

import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findAllByLesson_Id(Long lessonId);
}
