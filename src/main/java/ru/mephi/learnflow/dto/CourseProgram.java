package ru.mephi.learnflow.dto;

import java.util.List;

public record CourseProgram(CourseDetails course, List<Module> modules) {

    public record Module(CourseModuleDetails details, List<LessonDetails> lessons) {
    }
}
