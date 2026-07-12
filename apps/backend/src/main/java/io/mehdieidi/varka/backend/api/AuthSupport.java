package io.mehdieidi.varka.backend.api;

import io.mehdieidi.varka.platform.identity.application.AuthService;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import org.springframework.stereotype.Component;

/** Resolves authenticated users for controllers that require an auth token. */
@Component
public class AuthSupport {

  private final AuthService authService;

  /**
   * Creates controller authentication support.
   *
   * @param authService authentication service
   */
  public AuthSupport(AuthService authService) {
    this.authService = authService;
  }

  /**
   * Resolves the user represented by a required session token.
   *
   * @param token session token
   * @return authenticated user
   */
  public UserRecord user(String token) {
    return authService.requireUser(token);
  }
}
