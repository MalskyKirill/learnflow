package ru.mephi.learnflow.controller;

import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.mephi.learnflow.dto.CourseDetails;
import ru.mephi.learnflow.dto.EnrollmentDetails;
import ru.mephi.learnflow.dto.StudentDetails;
import ru.mephi.learnflow.security.LearnFlowPrincipal;
import ru.mephi.learnflow.service.EnrollmentService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class EnrollmentController {

    private final EnrollmentService enrollments;

    public EnrollmentController(EnrollmentService enrollments) {
        this.enrollments = enrollments;
    }

    @PostMapping("/courses/{courseId}/enrollment")
    public ResponseEntity<EnrollmentDetails> enroll(@PathVariable @Positive Long courseId,
                                                     @AuthenticationPrincipal LearnFlowPrincipal principal) {
        EnrollmentDetails enrollment = enrollments.enrollStudent(courseId, principal.getUserId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri();
        return ResponseEntity.created(location).body(enrollment);
    }

    @GetMapping("/courses/{courseId}/enrollment")
    public EnrollmentDetails getEnrollment(@PathVariable @Positive Long courseId,
                                           @AuthenticationPrincipal LearnFlowPrincipal principal) {
        return enrollments.getEnrollment(courseId, principal.getUserId());
    }

    @DeleteMapping("/courses/{courseId}/enrollment")
    public ResponseEntity<Void> unenroll(@PathVariable @Positive Long courseId,
                                         @AuthenticationPrincipal LearnFlowPrincipal principal) {
        enrollments.unenrollStudent(courseId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/courses")
    public List<CourseDetails> listMyCourses(@AuthenticationPrincipal LearnFlowPrincipal principal) {
        return enrollments.listCourses(principal.getUserId());
    }

    @GetMapping("/courses/{courseId}/students")
    public List<StudentDetails> listStudents(@PathVariable @Positive Long courseId,
                                             @AuthenticationPrincipal LearnFlowPrincipal principal) {
        return enrollments.listStudents(courseId, principal.getUserId());
    }
}
