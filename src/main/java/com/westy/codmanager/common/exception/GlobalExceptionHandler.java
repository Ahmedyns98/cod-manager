package com.westy.codmanager.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error leaves the API as an RFC 9457 ProblemDetail, so clients only ever
 * have to parse one error shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), "not-found");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        ProblemDetail detail = problem(HttpStatus.CONFLICT, "Business rule violated",
                ex.getMessage(), "business-rule");
        detail.setProperty("code", ex.getCode());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", "validation");
        detail.setProperty("errors", fieldErrors);
        return detail;
    }

    /*
     * Constraints on request parameters (@Min, @Max on a @Validated controller)
     * surface as ConstraintViolationException rather than as a binding failure.
     * Without this the caller gets a 500 for what is plainly a bad request.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleParameterViolation(ConstraintViolationException ex) {
        Map<String, String> violations = new LinkedHashMap<>();

        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;

            violations.putIfAbsent(field, violation.getMessage());
        });

        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more parameters are invalid", "validation");
        detail.setProperty("errors", violations);

        return detail;
    }

    private ProblemDetail problem(HttpStatus status, String title, String message, String type) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        detail.setType(URI.create("https://api.cod-manager.dev/errors/" + type));
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }
}
