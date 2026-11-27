package ru.mephi.learnflow.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.mephi.learnflow.dto.AssignmentDetails;
import ru.mephi.learnflow.dto.request.CreateAssignmentRequest;
import ru.mephi.learnflow.security.LearnFlowPrincipal;
import ru.mephi.learnflow.service.AssignmentService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AssignmentController {

    private final AssignmentService assignments;

    public AssignmentController(AssignmentService assignments) {
        this.assignments = assignments;
    }

    @PostMapping("/lessons/{lessonId}/assignments")
    public ResponseEntity<AssignmentDetails> createAssignment(@PathVariable @Positive Long lessonId,
            @Valid @RequestBody CreateAssignmentRequest request, @AuthenticationPrincipal LearnFlowPrincipal principal) {
        AssignmentDetails assignment = assignments.createAssignment(lessonId, principal.getUserId(), request.title(),
                request.description(), request.dueAt(), request.maxScore());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/assignments/{assignmentId}").buildAndExpand(assignment.id()).toUri();
        return ResponseEntity.created(location).body(assignment);
    }

    @GetMapping("/lessons/{lessonId}/assignments")
    public List<AssignmentDetails> listAssignments(@PathVariable @Positive Long lessonId) {
        return assignments.listAssignments(lessonId);
    }

    @GetMapping("/assignments/{assignmentId}")
    public AssignmentDetails getAssignment(@PathVariable @Positive Long assignmentId) {
        return assignments.getAssignment(assignmentId);
    }
}
