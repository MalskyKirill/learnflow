package ru.mephi.learnflow.controller;

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
import ru.mephi.learnflow.dto.CourseModuleDetails;
import ru.mephi.learnflow.dto.CourseProgram;
import ru.mephi.learnflow.dto.LessonDetails;
import ru.mephi.learnflow.dto.request.CreateLessonRequest;
import ru.mephi.learnflow.dto.request.CreateModuleRequest;
import ru.mephi.learnflow.dto.request.UpdateLessonRequest;
import ru.mephi.learnflow.dto.request.UpdateModuleRequest;
import ru.mephi.learnflow.security.LearnFlowPrincipal;
import ru.mephi.learnflow.service.CourseContentService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CourseContentController {

    private final CourseContentService content;

    public CourseContentController(CourseContentService content) {
        this.content = content;
    }

    @GetMapping("/courses/{courseId}/program")
    public CourseProgram getProgram(@PathVariable @Positive Long courseId) {
        return content.getProgram(courseId);
    }

    @GetMapping("/courses/{courseId}/modules")
    public List<CourseModuleDetails> listModules(@PathVariable @Positive Long courseId) {
        return content.listModules(courseId);
    }

    @PostMapping("/courses/{courseId}/modules")
    public ResponseEntity<CourseModuleDetails> addModule(@PathVariable @Positive Long courseId,
                                                        @Valid @RequestBody CreateModuleRequest request,
                                                        @AuthenticationPrincipal LearnFlowPrincipal principal) {
        CourseModuleDetails module = content.addModule(courseId, principal.getUserId(), request.title());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/modules/{moduleId}").buildAndExpand(module.id()).toUri();
        return ResponseEntity.created(location).body(module);
    }

    @GetMapping("/modules/{moduleId}")
    public CourseModuleDetails getModule(@PathVariable @Positive Long moduleId) {
        return content.getModule(moduleId);
    }

    @PutMapping("/modules/{moduleId}")
    public CourseModuleDetails updateModule(@PathVariable @Positive Long moduleId,
                                            @Valid @RequestBody UpdateModuleRequest request,
                                            @AuthenticationPrincipal LearnFlowPrincipal principal) {
        return content.updateModule(moduleId, principal.getUserId(), request.title());
    }

    @DeleteMapping("/modules/{moduleId}")
    public ResponseEntity<Void> deleteModule(@PathVariable @Positive Long moduleId,
                                             @AuthenticationPrincipal LearnFlowPrincipal principal) {
        content.deleteModule(moduleId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/modules/{moduleId}/lessons")
    public ResponseEntity<LessonDetails> addLesson(@PathVariable @Positive Long moduleId,
                                                 @Valid @RequestBody CreateLessonRequest request,
                                                 @AuthenticationPrincipal LearnFlowPrincipal principal) {
        LessonDetails lesson = content.addLesson(moduleId, principal.getUserId(), request.title(), request.content());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/lessons/{lessonId}").buildAndExpand(lesson.id()).toUri();
        return ResponseEntity.created(location).body(lesson);
    }

    @GetMapping("/lessons/{lessonId}")
    public LessonDetails getLesson(@PathVariable @Positive Long lessonId) {
        return content.getLesson(lessonId);
    }

    @PutMapping("/lessons/{lessonId}")
    public LessonDetails updateLesson(@PathVariable @Positive Long lessonId,
                                      @Valid @RequestBody UpdateLessonRequest request,
                                      @AuthenticationPrincipal LearnFlowPrincipal principal) {
        return content.updateLesson(lessonId, principal.getUserId(), request.title(), request.content());
    }

    @DeleteMapping("/lessons/{lessonId}")
    public ResponseEntity<Void> deleteLesson(@PathVariable @Positive Long lessonId,
                                             @AuthenticationPrincipal LearnFlowPrincipal principal) {
        content.deleteLesson(lessonId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
