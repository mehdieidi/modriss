package io.mehdieidi.modriss.backend.api;

import io.mehdieidi.modriss.backend.admin.AdminAccessService;
import io.mehdieidi.modriss.backend.analytics.VisitorAnalyticsService;
import io.mehdieidi.modriss.backend.guest.GuestAccessService;
import io.mehdieidi.modriss.platform.identity.application.AuthService;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides registration, session, and current-user endpoints. */
@SuppressWarnings("unused")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);

  private final AuthService authService;
  private final AdminAccessService adminAccess;
  private final VisitorAnalyticsService analytics;
  private final GuestAccessService guests;
  private final PasswordResetEmailService passwordResetEmail;

  /**
   * Creates the authentication controller.
   *
   * @param authService authentication service
   */
  public AuthController(
      AuthService authService,
      AdminAccessService adminAccess,
      VisitorAnalyticsService analytics,
      GuestAccessService guests,
      PasswordResetEmailService passwordResetEmail) {
    this.authService = authService;
    this.adminAccess = adminAccess;
    this.analytics = analytics;
    this.guests = guests;
    this.passwordResetEmail = passwordResetEmail;
  }

  /**
   * Registers a user and creates their initial session.
   *
   * @param request registration details
   * @return session token and registered user
   */
  @PostMapping("/register")
  AuthResponse register(@Valid @RequestBody RegisterRequest request) {
    AuthService.AuthResult result =
        authService.register(request.email(), request.password(), request.displayName());
    return new AuthResponse(result.token(), UserDto.from(result.user(), false));
  }

  /**
   * Authenticates credentials and creates a session.
   *
   * @param request login credentials
   * @return session token and authenticated user
   */
  @PostMapping("/login")
  AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    AuthService.AuthResult result = authService.login(request.email(), request.password());
    adminAccess.requireEnabled(result.user());
    analytics.recordLogin(result.user(), httpRequest, request.publicIp());
    return new AuthResponse(result.token(), UserDto.from(result.user(), false));
  }

  /** Sends a reset link when an account exists, with the same response for every email address. */
  @PostMapping("/password-reset/request")
  PasswordResetRequestResponse requestPasswordReset(
      @Valid @RequestBody PasswordResetRequestPayload request) {
    passwordResetEmail.requireConfigured();
    authService
        .createPasswordReset(request.email())
        .ifPresent(
            reset -> {
              try {
                passwordResetEmail.send(reset);
              } catch (RuntimeException ex) {
                log.error("Could not deliver password reset email", ex);
              }
            });
    return new PasswordResetRequestResponse(
        "If an account with that email exists, a password reset link has been sent.");
  }

  /** Replaces the account password using a valid emailed token. */
  @PostMapping("/password-reset/complete")
  void completePasswordReset(@Valid @RequestBody CompletePasswordResetPayload request) {
    authService.resetPassword(request.token(), request.password());
  }

  /** Creates an isolated anonymous session. No client-provided identity is trusted. */
  @PostMapping("/guest")
  AuthResponse guest() {
    AuthService.AuthResult result = guests.createGuest();
    return new AuthResponse(result.token(), UserDto.from(result.user(), true));
  }

  /**
   * Returns the user associated with the supplied session token.
   *
   * @param token session token
   * @return current user
   */
  @GetMapping("/me")
  UserDto me(@RequestHeader("X-Auth-Token") String token) {
    UserRecord user = authService.requireUser(token);
    adminAccess.requireEnabled(user);
    return UserDto.from(user, guests.isGuest(user.id()));
  }

  /**
   * Updates the current user's display name.
   *
   * @param token session token
   * @param request updated profile details
   * @return updated user
   */
  @PutMapping("/me")
  UserDto updateMe(
      @RequestHeader("X-Auth-Token") String token, @Valid @RequestBody UpdateMeRequest request) {
    UserRecord user = authService.updateDisplayName(token, request.displayName());
    adminAccess.requireEnabled(user);
    return UserDto.from(user, guests.isGuest(user.id()));
  }

  /**
   * Invalidates the supplied session token.
   *
   * @param token session token
   */
  @PostMapping("/logout")
  void logout(@RequestHeader("X-Auth-Token") String token) {
    authService.logout(token);
  }

  /**
   * User registration payload.
   *
   * @param email unique user email
   * @param password account password
   * @param displayName user-facing display name
   */
  public record RegisterRequest(
      @Email @NotBlank String email,
      @Size(min = 8) String password,
      @NotBlank @Size(max = 80) String displayName) {}

  /**
   * User login payload.
   *
   * @param email registered user email
   * @param password account password
   */
  public record LoginRequest(
      @Email @NotBlank String email,
      @Size(min = 8) String password,
      @Size(max = 45) String publicIp) {}

  /** Email address submitted to request a password reset link. */
  public record PasswordResetRequestPayload(@Email @NotBlank String email) {}

  /** One-time reset token and replacement password. */
  public record CompletePasswordResetPayload(
      @NotBlank String token, @NotBlank @Size(min = 8) String password) {}

  /** Non-enumerating response returned for every reset email request. */
  public record PasswordResetRequestResponse(String message) {}

  /**
   * Current-user profile update payload.
   *
   * @param displayName replacement display name
   */
  public record UpdateMeRequest(@NotBlank @Size(max = 80) String displayName) {}

  /**
   * Successful authentication response.
   *
   * @param token created session token
   * @param user authenticated user
   */
  public record AuthResponse(String token, UserDto user) {}

  /**
   * Public representation of an authenticated user.
   *
   * @param id stable user identifier
   * @param email user email, omitted for anonymous guests
   * @param displayName user-facing display name
   * @param guest whether this is an anonymous account
   */
  public record UserDto(String id, String email, String displayName, boolean guest) {

    /**
     * Converts a persisted user into its API representation.
     *
     * @param user persisted user
     * @return public user representation
     */
    static UserDto from(UserRecord user, boolean guest) {
      return new UserDto(user.id(), guest ? null : user.email(), user.displayName(), guest);
    }
  }
}
