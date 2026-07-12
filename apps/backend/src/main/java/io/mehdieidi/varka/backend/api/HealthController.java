package io.mehdieidi.varka.backend.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes a lightweight health endpoint for backend availability checks. */
@RestController
public class HealthController {

  /**
   * Reports the service's current availability and response timestamp.
   *
   * @return health status payload
   */
  @GetMapping("/api/health")
  Map<String, Object> health() {
    return Map.of("status", "UP", "service", "varka-backend", "timestamp", Instant.now());
  }
}
