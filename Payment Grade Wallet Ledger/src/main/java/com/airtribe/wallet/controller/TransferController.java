package com.airtribe.wallet.controller;

import com.airtribe.wallet.security.CurrentUser;
import com.airtribe.wallet.exception.ApiException;

import com.airtribe.wallet.dto.TransferModels.*;
import com.airtribe.wallet.service.StoredHttpResponse;
import com.airtribe.wallet.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/transfers")
public class TransferController {
    private final TransferService transfers;

    public TransferController(TransferService transfers) { this.transfers = transfers; }

    @PostMapping("/topup")
    public ResponseEntity<String> topUp(
            Principal principal,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody MoneyMovementRequest request) {
        long userId = CurrentUser.id(principal);
        StoredHttpResponse response = transfers.topUp(userId, idempotencyKey, request);
        return response.toResponseEntity();
    }

    @PostMapping
    public ResponseEntity<String> transfer(
            Principal principal,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        long userId = CurrentUser.id(principal);
        StoredHttpResponse response = transfers.transfer(userId, idempotencyKey, request);
        return response.toResponseEntity();
    }

    @PostMapping("/withdraw")
    public ResponseEntity<String> withdraw(
            Principal principal,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody MoneyMovementRequest request) {
        long userId = CurrentUser.id(principal);
        StoredHttpResponse response = transfers.withdraw(userId, idempotencyKey, request);
        return response.toResponseEntity();
    }

    @PostMapping("/{transactionId}/reverse")
    public ResponseEntity<String> reverse(
            Principal principal, @PathVariable @Positive long transactionId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody(required = false) String body) {
        if (body != null && !body.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST", "A reversal does not accept a request body.");
        }
        long userId = CurrentUser.id(principal);
        StoredHttpResponse response = transfers.reverse(userId, transactionId, idempotencyKey);
        return response.toResponseEntity();
    }

    @GetMapping("/{transactionId}")
    public TransferDetailsResponse getTransfer(Principal principal, @PathVariable @Positive long transactionId) {
        return transfers.getTransfer(CurrentUser.id(principal), transactionId);
    }
}
