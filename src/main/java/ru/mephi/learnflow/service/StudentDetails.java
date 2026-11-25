package ru.mephi.learnflow.service;

import ru.mephi.learnflow.entity.User;

public record StudentDetails(Long id, String name) {

    static StudentDetails from(User student) {
        return new StudentDetails(student.getId(), student.getName());
    }
}
