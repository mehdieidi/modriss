package io.mehdieidi.modless.backend.api;

import io.mehdieidi.modless.platform.core.PlatformException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PlatformException.class)
    ResponseEntity<ApiErrorResponse> platform(PlatformException ex) {
        if (ex.status() >= 500) {
            log.error("Platform error: {}", ex.getMessage(), ex);
        } else {
            log.warn("Request rejected: {}", ex.getMessage());
        }
        return ResponseEntity.status(ex.status())
                .body(new ApiErrorResponse(ex.getMessage(), ex.status(), Instant.now(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex) {
        List<String> issues = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("Request validation failed.", 400, Instant.now(),
                        issues));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception ex) {
        log.error("Unhandled backend exception", ex);
        return ResponseEntity.internalServerError()
                .body(new ApiErrorResponse("Unexpected backend error.", 500, Instant.now(),
                        List.of()));
    }
}
