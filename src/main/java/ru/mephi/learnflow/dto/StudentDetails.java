package ru.mephi.learnflow.dto;

import ru.mephi.learnflow.entity.User;

public record StudentDetails(Long id, String name) {

    public static StudentDetails from(User student) {
        return new StudentDetails(student.getId(), student.getName());
    }
}
