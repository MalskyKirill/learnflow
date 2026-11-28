package ru.mephi.learnflow.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.learnflow.entity.QuizSubmission;

import java.util.List;
import java.util.Optional;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {
    boolean existsByQuiz_Id(Long quizId);

    boolean existsByAnswers_Question_Id(Long questionId);

    @EntityGraph(attributePaths = "answers")
    List<QuizSubmission> findAllByStudent_IdOrderByTakenAtDescIdDesc(Long studentId);

    @EntityGraph(attributePaths = "answers")
    List<QuizSubmission> findAllByQuiz_IdOrderByTakenAtDescIdDesc(Long quizId);

    @EntityGraph(attributePaths = {"answers", "quiz.module.course"})
    Optional<QuizSubmission> findWithAnswersAndCourseById(Long id);
}
