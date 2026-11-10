package com.airtribe.wallet.controller;

import com.airtribe.wallet.exception.ApiException;
import com.airtribe.wallet.security.CurrentUser;
import com.airtribe.wallet.service.TransferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/admin/transfers")
public class AdminTransferController {
    private final TransferService transfers;

    public AdminTransferController(TransferService transfers) { this.transfers = transfers; }

        @PostMapping("/{transactionId}/release")
    public ResponseEntity<String> release(
            Principal principal, HttpServletRequest request, @PathVariable @Positive long transactionId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody(required = false) String body) {
        long adminId = CurrentUser.id(principal);
        if (!request.isUserInRole("ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "An administrator must release held transfers.");
        }
        if (body != null && !body.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "A release does not accept a request body.");
        }
        return transfers.releaseHeld(adminId, transactionId, idempotencyKey).toResponseEntity();
    }
}
