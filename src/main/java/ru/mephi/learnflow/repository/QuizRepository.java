package ru.mephi.learnflow.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.learnflow.entity.Question;
import ru.mephi.learnflow.entity.Quiz;

import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
    Optional<Quiz> findByModule_Id(Long moduleId);

    boolean existsByModule_Id(Long moduleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Quiz> findForUpdateById(Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Quiz> findForAttemptById(Long id);

    @EntityGraph(attributePaths = "module.course")
    Optional<Quiz> findWithCourseById(Long id);

    @EntityGraph(attributePaths = {"module.course", "questions"})
    Optional<Quiz> findWithQuestionsById(Long id);

    @EntityGraph(attributePaths = {"module.course", "questions"})
    Optional<Quiz> findWithQuestionsByModule_Id(Long moduleId);

    // Варианты загружаются отдельно, чтобы Hibernate не соединял две коллекции List.
    @Query("select q from Question q left join fetch q.options where q.quiz.id = :quizId")
    List<Question> findQuestionsWithOptionsByQuizId(@Param("quizId") Long quizId);

    @Query("""
            select q from Question q left join fetch q.options
            where q.quiz.id = :quizId and q.id = :questionId
            """)
    Optional<Question> findQuestionWithOptions(@Param("quizId") Long quizId, @Param("questionId") Long questionId);
}
