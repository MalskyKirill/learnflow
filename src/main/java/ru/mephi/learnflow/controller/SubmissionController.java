package ru.mephi.learnflow.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.mephi.learnflow.dto.SubmissionDetails;
import ru.mephi.learnflow.dto.request.CreateSubmissionRequest;
import ru.mephi.learnflow.dto.request.GradeSubmissionRequest;
import ru.mephi.learnflow.security.LearnFlowPrincipal;
import ru.mephi.learnflow.service.SubmissionService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SubmissionController {

    private final SubmissionService submissions;

    public SubmissionController(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @PostMapping("/assignments/{assignmentId}/submissions")
    public ResponseEntity<SubmissionDetails> submit(@PathVariable @Positive Long assignmentId,
            @Valid @RequestBody CreateSubmissionRequest request, @AuthenticationPrincipal LearnFlowPrincipal principal) {
        SubmissionDetails submission = submissions.submitAssignment(
                assignmentId, principal.getUserId(), request.content());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/submissions/{submissionId}").buildAndExpand(submission.id()).toUri();
        return ResponseEntity.created(location).body(submission);
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    public List<SubmissionDetails> listAssignmentSubmissions(@PathVariable @Positive Long assignmentId,
            @AuthenticationPrincipal LearnFlowPrincipal principal) {
        return submissions.listAssignmentSubmissions(assignmentId, principal.getUserId());
    }

    @GetMapping("/submissions/{submissionId}")
    public SubmissionDetails getSubmission(@PathVariable @Positive Long submissionId,
            @AuthenticationPrincipal LearnFlowPrincipal principal) {
        return submissions.getSubmission(submissionId, principal.getUserId());
    }

    @PutMapping("/submissions/{submissionId}/grade")
    public SubmissionDetails grade(@PathVariable @Positive Long submissionId,
            @Valid @RequestBody GradeSubmissionRequest request, @AuthenticationPrincipal LearnFlowPrincipal principal) {
        return submissions.gradeSubmission(submissionId, principal.getUserId(), request.score(), request.feedback());
    }

    @GetMapping("/me/submissions")
    public List<SubmissionDetails> listMySubmissions(@AuthenticationPrincipal LearnFlowPrincipal principal) {
        return submissions.listStudentSubmissions(principal.getUserId());
    }
}
