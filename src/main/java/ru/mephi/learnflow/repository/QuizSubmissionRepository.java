package ru.mephi.learnflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.learnflow.entity.QuizSubmission;

import java.util.List;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {
    List<QuizSubmission> findAllByStudent_IdOrderByTakenAtDescIdDesc(Long studentId);

    List<QuizSubmission> findAllByQuiz_IdOrderByTakenAtDescIdDesc(Long quizId);
}
