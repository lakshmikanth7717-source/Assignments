package com.airtribe.wallet.controller;

import com.airtribe.wallet.dto.RiskModels.*;
import com.airtribe.wallet.security.AdminAccess;
import com.airtribe.wallet.service.TransferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/admin/risk")
public class AdminRiskController {
    private final TransferService transfers;
    public AdminRiskController(TransferService transfers) { this.transfers = transfers; }

    @GetMapping("/queue")
    public QueueResponse queue(HttpServletRequest request,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        AdminAccess.requireAdmin(request);
        return transfers.getRiskQueue(page, size);
    }

    @PostMapping("/{transactionId}/decision")
    public ResponseEntity<String> decide(HttpServletRequest request, @PathVariable @Positive long transactionId,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody DecisionRequest decision) {
        long adminId = AdminAccess.requireAdmin(request);
        return transfers.decideRisk(adminId, transactionId, key, decision).toResponseEntity();
    }
}
