package io.mehdieidi.varka.backend.api;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.transformation.synchronization.TransformationValidationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps backend exceptions to stable API error responses and appropriate logging. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String REQUEST_ID_MDC_KEY = "requestId";

  /**
   * Maps a platform kernel failure to its declared HTTP status.
   *
   * @param ex platform failure
   * @return API error response
   */
  @ExceptionHandler(PlatformException.class)
  ResponseEntity<ApiErrorResponse> platform(PlatformException ex) {
    if (ex.status() >= 500) {
      log.error("Platform error [{}]: {}", errorId(), ex.getMessage(), ex);
    } else {
      log.warn("Request rejected [{}]: {}", errorId(), ex.getMessage());
    }
    return ResponseEntity.status(ex.status()).body(error(ex.status(), ex.getMessage(), List.of()));
  }

  /** Returns target-model findings when a synchronization finalization cannot be committed. */
  @ExceptionHandler(TransformationValidationException.class)
  ResponseEntity<ApiErrorResponse> transformationValidation(TransformationValidationException ex) {
    log.warn("Request rejected [{}]: {}", errorId(), ex.getMessage());
    return ResponseEntity.status(ex.status())
        .body(
            error(
                ex.status(),
                ex.getMessage(),
                ex.issues().stream().map(this::validationIssue).toList()));
  }

  /**
   * Maps bean-validation failures to a bad-request response with field issues.
   *
   * @param ex request validation failure
   * @return API error response
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex) {
    List<String> issues =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
    return ResponseEntity.badRequest().body(error(400, "Request validation failed.", issues));
  }

  /**
   * Maps missing routes/static resources to not-found instead of an internal error.
   *
   * @param ex missing route/resource failure
   * @return API error response
   */
  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiErrorResponse> missingResource(NoResourceFoundException ex) {
    log.warn("Route not found [{}]: {}", errorId(), ex.getResourcePath());
    return ResponseEntity.status(404).body(error(404, "Resource not found.", List.of()));
  }

  /**
   * Converts an unhandled exception to a generic internal-server-error response.
   *
   * @param ex unexpected failure
   * @return API error response that does not expose internal details
   */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unexpected(Exception ex) {
    log.error("Unhandled backend exception [{}]", errorId(), ex);
    return ResponseEntity.internalServerError()
        .body(error(500, "An unexpected server error occurred.", List.of()));
  }

  private ApiErrorResponse error(int status, String detailMessage, List<?> issues) {
    return new ApiErrorResponse(
        clientMessage(status, detailMessage), status, Instant.now(), issues, errorId());
  }

  private String validationIssue(
      io.mehdieidi.varka.platform.model.application.ModelService.ValidationIssue issue) {
    String element =
        issue.elementName() == null || issue.elementName().isBlank()
            ? issue.elementId()
            : issue.elementName();
    String location = element == null || element.isBlank() ? "" : element + ": ";
    return location + issue.constraint() + ": " + issue.message();
  }

  private String clientMessage(int status, String detailMessage) {
    if (exposeDetailMessage(status)) {
      return detailMessage;
    }
    return genericServerMessage(status);
  }

  private boolean exposeDetailMessage(int status) {
    return status < 500 || status == 501;
  }

  private String genericServerMessage(int status) {
    return switch (status) {
      case 502 -> "A backend integration failed.";
      case 503 -> "The service is temporarily unavailable.";
      default -> "An unexpected server error occurred.";
    };
  }

  private String errorId() {
    String requestId = MDC.get(REQUEST_ID_MDC_KEY);
    return StringUtils.hasText(requestId) ? requestId : UUID.randomUUID().toString();
  }
}
