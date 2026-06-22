package io.mehdieidi.modless.backend.api;

import java.time.Instant;
import java.util.List;

/**
 * Standard JSON body returned for backend API failures.
 *
 * @param message human-readable error summary safe to show API callers
 * @param status HTTP status code
 * @param timestamp time at which the response was created
 * @param issues optional detailed validation or processing issues for client errors
 * @param errorId correlation id for support and log lookup
 */
public record ApiErrorResponse(
    String message, int status, Instant timestamp, List<?> issues, String errorId) {}
