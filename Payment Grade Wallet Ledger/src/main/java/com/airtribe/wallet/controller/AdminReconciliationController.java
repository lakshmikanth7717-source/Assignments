package com.airtribe.wallet.controller;

import com.airtribe.wallet.dto.ReconciliationModels.RunReport;
import com.airtribe.wallet.exception.ApiException;
import com.airtribe.wallet.security.AdminAccess;
import com.airtribe.wallet.service.ReconciliationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/admin/reconciliation")
public class AdminReconciliationController {
    private final ReconciliationService reconciliation;
    public AdminReconciliationController(ReconciliationService reconciliation) { this.reconciliation = reconciliation; }

    @PostMapping("/run")
    public ResponseEntity<String> run(HttpServletRequest request,
            @RequestHeader("Idempotency-Key") UUID key, @RequestBody(required = false) String body) {
        long adminId = AdminAccess.requireAdmin(request);
        if (body != null && !body.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "This endpoint takes no request body.");
        }
        return reconciliation.enqueue(adminId, key).toResponseEntity();
    }

    @GetMapping("/runs/{runId}")
    public RunReport report(HttpServletRequest request, @PathVariable @Positive long runId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        AdminAccess.requireAdmin(request);
        return reconciliation.getReport(runId, page, size);
    }
}
