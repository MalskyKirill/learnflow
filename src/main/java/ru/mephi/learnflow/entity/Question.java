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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import ru.mephi.learnflow.exception.InvalidInputException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @OneToMany(mappedBy = "question", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<AnswerOption> options = new ArrayList<>();

    protected Question() {
    }

    Question(Quiz quiz, String text) {
        this.quiz = quiz;
        changeText(text);
    }

    public void changeText(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidInputException("Текст вопроса не должен быть пустым");
        }
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public String getText() {
        return text;
    }

    public List<AnswerOption> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public AnswerOption addOption(String text, boolean correct) {
        AnswerOption option = new AnswerOption(this, text, correct);
        options.add(option);
        return option;
    }

    public void clearOptions() {
        options.clear();
    }
}
