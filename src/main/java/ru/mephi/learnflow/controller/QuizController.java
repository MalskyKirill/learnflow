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
import ru.mephi.learnflow.dto.QuizDetails;
import ru.mephi.learnflow.dto.QuizOptionDraft;
import ru.mephi.learnflow.dto.QuizQuestionDetails;
import ru.mephi.learnflow.dto.QuizView;
import ru.mephi.learnflow.dto.request.CreateQuizQuestionRequest;
import ru.mephi.learnflow.dto.request.CreateQuizRequest;
import ru.mephi.learnflow.security.LearnFlowPrincipal;
import ru.mephi.learnflow.service.QuizService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class QuizController {

    private final QuizService quizzes;

    public QuizController(QuizService quizzes) {
        this.quizzes = quizzes;
    }

    @PostMapping("/modules/{moduleId}/quiz")
    public ResponseEntity<QuizDetails> createQuiz(@PathVariable @Positive Long moduleId,
            @Valid @RequestBody CreateQuizRequest request, @AuthenticationPrincipal LearnFlowPrincipal principal) {
        QuizDetails quiz = quizzes.createQuiz(moduleId, principal.getUserId(), request.title());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri();
        return ResponseEntity.created(location).body(quiz);
    }

    @GetMapping("/modules/{moduleId}/quiz")
    public QuizView getQuizByModule(@PathVariable @Positive Long moduleId,
            @AuthenticationPrincipal LearnFlowPrincipal principal) {
        return quizzes.getQuizByModule(moduleId, principal.getUserId());
    }

    @GetMapping("/quizzes/{quizId}")
    public QuizView getQuiz(@PathVariable @Positive Long quizId, @AuthenticationPrincipal LearnFlowPrincipal principal) {
        return quizzes.getQuiz(quizId, principal.getUserId());
    }

    @PostMapping("/quizzes/{quizId}/questions")
    public ResponseEntity<QuizQuestionDetails> addQuestion(@PathVariable @Positive Long quizId,
            @Valid @RequestBody CreateQuizQuestionRequest request,
            @AuthenticationPrincipal LearnFlowPrincipal principal) {
        List<QuizOptionDraft> options = request.options().stream()
                .map(option -> new QuizOptionDraft(option.text(), option.correct()))
                .toList();
        QuizQuestionDetails question = quizzes.addQuestion(quizId, principal.getUserId(), request.text(), options);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{questionId}").buildAndExpand(question.id()).toUri();
        return ResponseEntity.created(location).body(question);
    }

    @GetMapping("/quizzes/{quizId}/questions/{questionId}")
    public QuizQuestionDetails getQuestion(@PathVariable @Positive Long quizId,
            @PathVariable @Positive Long questionId, @AuthenticationPrincipal LearnFlowPrincipal principal) {
        return quizzes.getQuestionForTeacher(quizId, questionId, principal.getUserId());
    }
}
