package com.airtribe.wallet.controller;

import com.airtribe.wallet.security.CurrentUser;

import com.airtribe.wallet.dto.WalletModels.*;
import com.airtribe.wallet.service.StoredHttpResponse;
import com.airtribe.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
public class WalletController {
    private final WalletService wallets;

    public WalletController(WalletService wallets) { this.wallets = wallets; }

    @PostMapping("/users/{userId}/wallets")
    public ResponseEntity<String> openWallet(
            Principal principal, @PathVariable @Positive long userId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody OpenWalletRequest request) {
        long actorUserId = CurrentUser.id(principal);
        StoredHttpResponse response = wallets.openWallet(actorUserId, userId, idempotencyKey, request);
        return response.toResponseEntity();
    }

    @GetMapping("/wallets/{walletId}/balance")
    public BalanceResponse getBalance(Principal principal, @PathVariable @Positive long walletId) {
        return wallets.getBalance(CurrentUser.id(principal), walletId);
    }

    @GetMapping("/wallets/{walletId}/statement")
    public StatementResponse getStatement(
            Principal principal, @PathVariable @Positive long walletId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Min(0) Long offset,
            @RequestParam(defaultValue = "offset") String pagination,
            @RequestParam(required = false) String cursor) {
        return wallets.getStatement(CurrentUser.id(principal), walletId, page, size, offset, pagination, cursor);
    }
}
