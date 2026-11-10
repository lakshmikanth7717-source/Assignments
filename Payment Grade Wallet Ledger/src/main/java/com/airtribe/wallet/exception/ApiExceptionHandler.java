package com.airtribe.wallet.exception;

import com.airtribe.wallet.security.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> apiError(ApiException error, HttpServletRequest request) {
        return response(error.status().value(), error.code(), error.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException error, HttpServletRequest request) {
        Map<String, String> fields = new TreeMap<>();
        error.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        String message = fields.entrySet().stream().findFirst()
                .map(field -> field.getKey() + ": " + field.getValue()).orElse("Request validation failed.");
        return response(400, "INVALID_REQUEST", message, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ServletRequestBindingException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class})
    public ResponseEntity<ApiError> invalidRequest(Exception error, HttpServletRequest request) {
        return response(400, "INVALID_REQUEST", "Check the request body, headers, and parameters.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpectedError(Exception error, HttpServletRequest request) {
        if (error instanceof ErrorResponse httpError) {
            return ResponseEntity.status(httpError.getStatusCode()).headers(httpError.getHeaders())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiError("HTTP_ERROR", "The HTTP request could not be handled.", RequestIdFilter.requestId(request)));
        }
        LOG.error("Unhandled wallet API failure request_id={}", RequestIdFilter.requestId(request), error);
        return response(500, "INTERNAL_ERROR", "The request could not be completed.", request);
    }

    public static ResponseEntity<ApiError> response(int status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(code, message, RequestIdFilter.requestId(request)));
    }
}
