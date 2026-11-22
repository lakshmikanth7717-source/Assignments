package com.taskmanagement.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    public record ErrorView(Instant timestamp, int status, String error, String message, String path,
                            Map<String, String> fields) {
    }

    public static ErrorView body(HttpStatus status, String message, String path, Map<String, String> fields) {
        return new ErrorView(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fields);
    }

    private ResponseEntity<ErrorView> response(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(body(status, message, request.getRequestURI(), Map.of()));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorView> api(ApiException ex, HttpServletRequest request) {
        return response(ex.status, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorView> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI(), fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class, MissingServletRequestParameterException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<ErrorView> malformed(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Invalid request body, parameter, or missing required field", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorView> tooLarge(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "Attachments must not exceed 10 MiB", request);
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ErrorView> multipart(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Invalid multipart request", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorView> integrity(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "Resource already exists or conflicts with related data", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorView> concurrent(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "Task changed; reload it before updating", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorView> notFound(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "Endpoint not found", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorView> method(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorView> media(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type not supported", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorView> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API error on {}", request.getRequestURI(), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }
}
