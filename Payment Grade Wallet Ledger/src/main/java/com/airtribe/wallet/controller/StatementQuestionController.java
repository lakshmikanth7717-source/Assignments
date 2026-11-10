package com.airtribe.wallet.controller;

import com.airtribe.wallet.dto.StatementQuestionModels.Question;
import com.airtribe.wallet.security.CurrentUser;
import com.airtribe.wallet.service.StatementQuestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

@RestController
public class StatementQuestionController {
    private final StatementQuestionService questions;
    public StatementQuestionController(StatementQuestionService questions) { this.questions = questions; }
    @PostMapping("/users/{userId}/statement/ask")
    public ResponseEntity<String> ask(Principal principal, @PathVariable @Positive long userId,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody Question question) {
        return questions.ask(CurrentUser.id(principal), userId, key, question).toResponseEntity();
    }
}
