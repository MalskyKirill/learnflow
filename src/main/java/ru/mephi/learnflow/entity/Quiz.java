package ru.mephi.learnflow.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "quizzes")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false, unique = true)
    private CourseModule module;

    @Column(nullable = false)
    private String title;

    @OneToMany(mappedBy = "quiz", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Question> questions = new ArrayList<>();

    protected Quiz() {
    }

    public Quiz(CourseModule module, String title) {
        if (title == null || title.isBlank() || title.length() > 255) {
            throw new IllegalArgumentException("Название викторины должно содержать от 1 до 255 символов");
        }
        this.module = module;
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public CourseModule getModule() {
        return module;
    }

    public String getTitle() {
        return title;
    }

    public List<Question> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public Question addQuestion(String text) {
        Question question = new Question(this, text);
        questions.add(question);
        return question;
    }

    public List<AnswerOption> resolveSelectedOptions(Map<Long, Long> answers) {
        if (answers == null) {
            throw new IllegalArgumentException("Передайте ответы на вопросы викторины");
        }
        Map<Long, Long> remaining = new LinkedHashMap<>(answers);
        List<AnswerOption> selected = new ArrayList<>(answers.size());
        for (Question question : questions) {
            Long questionId = question.getId();
            if (questionId == null || !remaining.containsKey(questionId)) {
                continue;
            }
            Long optionId = remaining.remove(questionId);
            if (optionId == null) {
                throw new IllegalArgumentException("Укажите вариант ответа для вопроса " + questionId);
            }
            AnswerOption option = question.getOptions().stream()
                    .filter(candidate -> optionId.equals(candidate.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Вариант ответа " + optionId + " не относится к вопросу " + questionId));
            selected.add(option);
        }
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException(
                    "Вопрос " + remaining.keySet().iterator().next() + " не относится к викторине");
        }
        return List.copyOf(selected);
    }

    public QuizSubmission gradeAttempt(User student, Map<Long, Long> answers) {
        List<AnswerOption> selected = resolveSelectedOptions(answers);
        int score = (int) selected.stream().filter(AnswerOption::isCorrect).count();
        QuizSubmission attempt = new QuizSubmission(this, student, score);
        selected.forEach(attempt::addAnswer);
        return attempt;
    }
}
