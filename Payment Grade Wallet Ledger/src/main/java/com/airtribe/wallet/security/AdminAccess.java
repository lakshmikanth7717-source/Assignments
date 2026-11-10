package com.airtribe.wallet.security;

import com.airtribe.wallet.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

public final class AdminAccess {
    private AdminAccess() { }
    public static long requireAdmin(HttpServletRequest request) {
        long userId = CurrentUser.id(request.getUserPrincipal());
        if (!request.isUserInRole("ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "An administrator is required.");
        }
        return userId;
    }
}
