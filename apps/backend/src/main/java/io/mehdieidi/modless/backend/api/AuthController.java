package io.mehdieidi.modless.backend.api;

import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides registration, session, and current-user endpoints. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  /**
   * Creates the authentication controller.
   *
   * @param authService authentication service
   */
  public AuthController(AuthService authService) {
    this.authService = authService;
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
    return new AuthResponse(result.token(), UserDto.from(result.user()));
  }

  /**
   * Authenticates credentials and creates a session.
   *
   * @param request login credentials
   * @return session token and authenticated user
   */
  @PostMapping("/login")
  AuthResponse login(@Valid @RequestBody LoginRequest request) {
    AuthService.AuthResult result = authService.login(request.email(), request.password());
    return new AuthResponse(result.token(), UserDto.from(result.user()));
  }

  /**
   * Returns the user associated with the supplied session token.
   *
   * @param token session token
   * @return current user
   */
  @GetMapping("/me")
  UserDto me(@RequestHeader("X-Auth-Token") String token) {
    return UserDto.from(authService.requireUser(token));
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
    return UserDto.from(authService.updateDisplayName(token, request.displayName()));
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
  public record LoginRequest(@Email @NotBlank String email, @Size(min = 8) String password) {}

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
   * @param email user email
   * @param displayName user-facing display name
   */
  public record UserDto(String id, String email, String displayName) {

    /**
     * Converts a persisted user into its API representation.
     *
     * @param user persisted user
     * @return public user representation
     */
    static UserDto from(UserRecord user) {
      return new UserDto(user.id(), user.email(), user.displayName());
    }
  }
}
