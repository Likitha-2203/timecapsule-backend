package com.example.timecapsule.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/** Every error is returned as {"error": "..."} so the frontend can show it directly. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        Map<String, Object> body = new HashMap<>(e.getExtra());
        body.put("error", e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String field = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f -> f.getField()).orElse("input");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Please enter a valid value for: " + field + "."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "The request could not be read. Please check the details you entered."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("Unhandled exception: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred. Please try again later."));
    }
}