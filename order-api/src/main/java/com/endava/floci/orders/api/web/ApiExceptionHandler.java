package com.endava.floci.orders.api.web;

import com.endava.floci.orders.api.service.OrderExceptions;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid order request");
        problem.setProperty("fieldErrors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({
        ConstraintViolationException.class,
        MissingRequestHeaderException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetail> badRequest(Exception ex) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, safeMessage(ex)));
    }

    @ExceptionHandler(OrderExceptions.NotFound.class)
    ResponseEntity<ProblemDetail> notFound(OrderExceptions.NotFound ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(OrderExceptions.IdempotencyConflict.class)
    ResponseEntity<ProblemDetail> conflict(OrderExceptions.IdempotencyConflict ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(OrderExceptions.DependencyFailure.class)
    ResponseEntity<ProblemDetail> dependency(OrderExceptions.DependencyFailure ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage()));
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://example.endava.com/problems/" + status.value()));
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }

    private static String safeMessage(Exception ex) {
        return ex instanceof HttpMessageNotReadableException
                ? "Malformed or unsupported JSON request"
                : ex.getMessage();
    }
}
