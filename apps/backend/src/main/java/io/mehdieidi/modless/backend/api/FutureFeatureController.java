package io.mehdieidi.modless.backend.api;

import io.mehdieidi.modless.platform.core.PlatformException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns an explicit not-implemented response for reserved future API namespaces. */
@RestController
public class FutureFeatureController {

  /** Rejects requests to endpoints reserved for future features. */
  @RequestMapping({"/api/github/**", "/api/impact/**", "/api/admin/**"})
  void future() {
    throw new PlatformException(501, "This feature is planned for a future backend iteration.");
  }
}
