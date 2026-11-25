package ru.mephi.learnflow.service;

import ru.mephi.learnflow.entity.CourseModule;

public record CourseModuleDetails(Long id, Long courseId, String title, int position) {

    static CourseModuleDetails from(CourseModule module) {
        return new CourseModuleDetails(module.getId(), module.getCourse().getId(),
                module.getTitle(), module.getPosition());
    }
}
