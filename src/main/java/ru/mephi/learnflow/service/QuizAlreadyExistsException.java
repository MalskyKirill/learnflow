package ru.mephi.learnflow.service;

public class QuizAlreadyExistsException extends RuntimeException {

    public QuizAlreadyExistsException(Long moduleId) {
        super("В модуле " + moduleId + " уже есть викторина");
    }
}
