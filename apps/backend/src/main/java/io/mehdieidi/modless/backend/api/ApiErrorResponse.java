package io.mehdieidi.modless.backend.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(String message, int status, Instant timestamp, List<?> issues) {

}
