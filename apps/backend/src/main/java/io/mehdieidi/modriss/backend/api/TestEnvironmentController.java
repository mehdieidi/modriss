package io.mehdieidi.modriss.backend.api;

import io.mehdieidi.modriss.backend.testing.TestEnvironmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.node.ObjectNode;

/** Project-scoped API for real AWS-emulator deployment and execution evidence. */
@RestController
@RequestMapping("/api/projects/{projectId}/test-environment")
public class TestEnvironmentController {
  private final AuthSupport auth;
  private final TestEnvironmentService testing;

  public TestEnvironmentController(AuthSupport auth, TestEnvironmentService testing) {
    this.auth = auth;
    this.testing = testing;
  }

  @GetMapping
  public ObjectNode overview(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @RequestParam(required = false) String before) {
    return testing.overview(auth.user(token), projectId, before);
  }

  @PostMapping("/jobs")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ObjectNode submit(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @RequestBody ObjectNode request) {
    return testing.submit(auth.user(token), projectId, request);
  }

  @GetMapping("/jobs/{id}")
  public ObjectNode get(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id) {
    return testing.get(auth.user(token), projectId, id);
  }

  @GetMapping(value = "/jobs/{id}/evidence", produces = "application/json")
  public org.springframework.http.ResponseEntity<ObjectNode> evidence(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id) {
    return org.springframework.http.ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"modriss-test-evidence.json\"")
        .body(testing.get(auth.user(token), projectId, id));
  }
}
