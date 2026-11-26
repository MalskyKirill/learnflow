package ru.mephi.learnflow.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.learnflow.entity.QuizSubmission;

import java.util.List;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {
    @EntityGraph(attributePaths = "answers")
    List<QuizSubmission> findAllByStudent_IdOrderByTakenAtDescIdDesc(Long studentId);

    @EntityGraph(attributePaths = "answers")
    List<QuizSubmission> findAllByQuiz_IdOrderByTakenAtDescIdDesc(Long quizId);
}
