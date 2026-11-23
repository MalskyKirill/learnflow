package ru.mephi.learnflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.learnflow.entity.Quiz;

import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
    Optional<Quiz> findByModule_Id(Long moduleId);
}
