package ru.mephi.learnflow.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "quiz_submissions")
@Check(constraints = "score >= 0")
public class QuizSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false)
    private int score;

    @Column(name = "taken_at", nullable = false, updatable = false)
    private Instant takenAt;

    @OneToMany(mappedBy = "submission", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StudentAnswer> answers = new ArrayList<>();

    protected QuizSubmission() {
    }

    public QuizSubmission(Quiz quiz, User student, int score) {
        if (student == null || student.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException("Пройти тест может только студент");
        }
        if (score < 0) {
            throw new IllegalArgumentException("Балл за тест должен быть неотрицательным");
        }
        this.quiz = quiz;
        this.student = student;
        this.score = score;
        this.takenAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public User getStudent() {
        return student;
    }

    public int getScore() {
        return score;
    }

    public Instant getTakenAt() {
        return takenAt;
    }

    public List<StudentAnswer> getAnswers() {
        return Collections.unmodifiableList(answers);
    }

    public void addAnswer(AnswerOption option) {
        Quiz questionQuiz = option.getQuestion().getQuiz();
        Long quizId = quiz.getId();
        if (questionQuiz != quiz && (quizId == null || !quizId.equals(questionQuiz.getId()))) {
            throw new IllegalArgumentException("Вариант ответа относится к другому тесту");
        }
        answers.add(new StudentAnswer(this, option));
    }
}
