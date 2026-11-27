package ru.mephi.learnflow.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.learnflow.dto.CourseDetails;
import ru.mephi.learnflow.service.CourseService;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courses;

    public CourseController(CourseService courses) {
        this.courses = courses;
    }

    @GetMapping
    public List<CourseDetails> listCourses() {
        return courses.listCourses();
    }
}
