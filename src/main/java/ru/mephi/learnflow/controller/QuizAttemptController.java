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
import ru.mephi.learnflow.dto.QuizAttemptDetails;
import ru.mephi.learnflow.dto.request.SubmitQuizAttemptRequest;
import ru.mephi.learnflow.security.LearnFlowPrincipal;
import ru.mephi.learnflow.service.QuizService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class QuizAttemptController {

    private final QuizService quizzes;

    public QuizAttemptController(QuizService quizzes) {
        this.quizzes = quizzes;
    }

    @PostMapping("/quizzes/{quizId}/attempts")
    public ResponseEntity<QuizAttemptDetails> submit(@PathVariable @Positive Long quizId,
            @Valid @RequestBody SubmitQuizAttemptRequest request, @AuthenticationPrincipal LearnFlowPrincipal principal) {
        QuizAttemptDetails attempt = quizzes.submitQuiz(quizId, principal.getUserId(), request.answers());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/quiz-attempts/{attemptId}").buildAndExpand(attempt.id()).toUri();
        return ResponseEntity.created(location).body(attempt);
    }

    @GetMapping("/quiz-attempts/{attemptId}")
    public QuizAttemptDetails getAttempt(@PathVariable @Positive Long attemptId,
            @AuthenticationPrincipal LearnFlowPrincipal principal) {
        return quizzes.getAttempt(attemptId, principal.getUserId());
    }

    @GetMapping("/me/quiz-attempts")
    public List<QuizAttemptDetails> listMyAttempts(@AuthenticationPrincipal LearnFlowPrincipal principal) {
        return quizzes.listStudentAttempts(principal.getUserId());
    }

    @GetMapping("/quizzes/{quizId}/attempts")
    public List<QuizAttemptDetails> listQuizAttempts(@PathVariable @Positive Long quizId,
            @AuthenticationPrincipal LearnFlowPrincipal principal) {
        return quizzes.listQuizAttempts(quizId, principal.getUserId());
    }
}
