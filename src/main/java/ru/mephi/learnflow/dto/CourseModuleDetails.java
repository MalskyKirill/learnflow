package ru.mephi.learnflow.dto;

import ru.mephi.learnflow.entity.CourseModule;

public record CourseModuleDetails(Long id, Long courseId, String title, int position) {

    public static CourseModuleDetails from(CourseModule module) {
        return new CourseModuleDetails(module.getId(), module.getCourse().getId(),
                module.getTitle(), module.getPosition());
    }
}
