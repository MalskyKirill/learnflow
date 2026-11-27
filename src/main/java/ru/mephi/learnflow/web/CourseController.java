package ru.mephi.learnflow.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.mephi.learnflow.dto.CourseDetails;
import ru.mephi.learnflow.dto.request.CreateCourseRequest;
import ru.mephi.learnflow.dto.request.UpdateCourseRequest;
import ru.mephi.learnflow.security.LearnFlowPrincipal;
import ru.mephi.learnflow.service.CourseService;

import java.net.URI;
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

    @GetMapping("/{courseId}")
    public CourseDetails getCourse(@PathVariable @Positive Long courseId) {
        return courses.getCourse(courseId);
    }

    @PostMapping
    public ResponseEntity<CourseDetails> createCourse(@Valid @RequestBody CreateCourseRequest request,
                                                      @AuthenticationPrincipal LearnFlowPrincipal principal) {
        CourseDetails course = courses.createCourse(
                request.title(), request.description(), request.categoryId(), principal.getUserId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{courseId}").buildAndExpand(course.id()).toUri();
        return ResponseEntity.created(location).body(course);
    }

    @PutMapping("/{courseId}")
    public CourseDetails updateCourse(@PathVariable @Positive Long courseId,
                                      @Valid @RequestBody UpdateCourseRequest request,
                                      @AuthenticationPrincipal LearnFlowPrincipal principal) {
        Long actorId = principal.getUserId();
        Long teacherId = request.teacherId() == null ? actorId : request.teacherId();
        return courses.updateCourse(courseId, actorId, request.title(), request.description(),
                request.categoryId(), teacherId);
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> deleteCourse(@PathVariable @Positive Long courseId,
                                             @AuthenticationPrincipal LearnFlowPrincipal principal) {
        courses.deleteCourse(courseId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
