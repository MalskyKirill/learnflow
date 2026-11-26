package ru.mephi.learnflow.exception;

public class AlreadySubmittedException extends RuntimeException {

    public AlreadySubmittedException(Long assignmentId, Throwable cause) {
        super("Решение задания " + assignmentId + " уже отправлено", cause);
    }
}
