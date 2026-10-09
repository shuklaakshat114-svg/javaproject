package com.healthcart.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Model model) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("timestamp", Instant.now().toString(), "status", 403, "error", "Forbidden", "message", "Access denied"));
        }
        model.addAttribute("errorCode", "403");
        model.addAttribute("errorTitle", "Access Denied");
        model.addAttribute("errorMessage", "You do not have permission to access this resource or manage another user's records.");
        return "error/403";
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Object handleConflict(ObjectOptimisticLockingFailureException ex, HttpServletRequest request, Model model) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Optimistic locking conflict [{}]: {}", traceId, ex.getMessage());

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("timestamp", Instant.now().toString(), "status", 409, "error", "Conflict", "traceId", traceId));
        }

        model.addAttribute("errorCode", "409");
        model.addAttribute("errorTitle", "Concurrency Conflict");
        model.addAttribute("errorMessage", "The record was updated by another user or order in parallel. Please refresh and try again.");
        return "error/500";
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleDomainBusinessError(Exception ex, HttpServletRequest request, Model model) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("timestamp", Instant.now().toString(), "status", 400, "error", "Bad Request", "message", ex.getMessage()));
        }

        model.addAttribute("errorCode", "400");
        model.addAttribute("errorTitle", "Action Failed");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/500";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleGeneralError(Exception ex, HttpServletRequest request, Model model) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("Internal Server Error [Ref: {}]", traceId, ex);

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("timestamp", Instant.now().toString(), "status", 500, "error", "Internal Server Error", "traceId", traceId));
        }

        model.addAttribute("errorCode", "500");
        model.addAttribute("errorTitle", "System Error");
        model.addAttribute("traceId", traceId);
        model.addAttribute("errorMessage", "An unexpected error occurred while processing your request. Please quote Reference ID: " + traceId);
        return "error/500";
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/");
    }
}
