package io.mehdieidi.varka.backend.api;

import io.mehdieidi.varka.backend.admin.AdminAccessService;
import io.mehdieidi.varka.platform.identity.application.AuthService;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import org.springframework.stereotype.Component;

/** Resolves authenticated users for controllers that require an auth token. */
@Component
public class AuthSupport {

  private final AuthService authService;
  private final AdminAccessService adminAccess;

  /**
   * Creates controller authentication support.
   *
   * @param authService authentication service
   */
  public AuthSupport(AuthService authService, AdminAccessService adminAccess) {
    this.authService = authService;
    this.adminAccess = adminAccess;
  }

  /**
   * Resolves the user represented by a required session token.
   *
   * @param token session token
   * @return authenticated user
   */
  public UserRecord user(String token) {
    UserRecord user = authService.requireUser(token);
    adminAccess.requireEnabled(user);
    return user;
  }
}
