package io.mehdieidi.modriss.platform.identity.application;

import io.mehdieidi.modriss.platform.identity.domain.AuthSession;
import io.mehdieidi.modriss.platform.identity.domain.EmailVerificationToken;
import io.mehdieidi.modriss.platform.identity.domain.PasswordResetToken;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.storage.api.PlatformStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages local user registration, password verification, and file-backed bearer sessions. */
public final class AuthService {

  /** Logger for account and session events. */
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  /** PBKDF2 iteration count used for password hashes. */
  private static final int ITERATIONS = 120_000;

  /** PBKDF2 output key length in bits. */
  private static final int KEY_BITS = 256;

  /** Lifetime of an emailed password reset link. */
  private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(30);

  /** Lifetime of an emailed email verification link. */
  private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);

  /** File repository used for users and sessions. */
  private final PlatformStore store;

  /** Secure random source for salts and tokens. */
  private final SecureRandom random = new SecureRandom();

  /** Lifetime assigned to newly issued sessions. */
  private final Duration sessionTtl;

  /**
   * Creates an authentication service.
   *
   * @param store backing JSON repository
   * @param sessionTtl lifetime assigned to new sessions
   */
  public AuthService(PlatformStore store, Duration sessionTtl) {
    this.store = store;
    this.sessionTtl = sessionTtl;
  }

  /**
   * Creates a verified account and session for trusted internal account creation.
   *
   * <p>Public self-registration must use {@link #registerPendingVerification(String, String,
   * String)} so the account cannot sign in until the email address is verified.
   *
   * @param email user email address
   * @param password plain-text password to hash
   * @param displayName display name
   * @return issued authentication result
   */
  public AuthResult register(String email, String password, String displayName) {
    String normalizedEmail = normalizeEmail(email);
    validatePassword(password);
    if (findByEmail(normalizedEmail) != null) {
      throw new PlatformException(409, "An account with this email already exists.");
    }
    Instant now = Instant.now();
    String salt = randomToken(24);
    UserRecord user =
        new UserRecord(
            UUID.randomUUID().toString(),
            normalizedEmail,
            requireText(displayName, "Display name is required."),
            hashPassword(password, salt),
            salt,
            now,
            now);
    store.write(Path.of("users", user.id() + ".json"), user);
    log.info("Registered user {}", user.email());
    return issueSession(user);
  }

  /** Creates a public registration and a one-time email verification request without a session. */
  public EmailVerificationRequest registerPendingVerification(
      String email, String password, String displayName) {
    return store.inTransaction(
        () -> {
          String normalizedEmail = normalizeEmail(email);
          validatePassword(password);
          if (findByEmail(normalizedEmail) != null) {
            throw new PlatformException(409, "An account with this email already exists.");
          }
          Instant now = Instant.now();
          String salt = randomToken(24);
          UserRecord user =
              new UserRecord(
                  UUID.randomUUID().toString(),
                  normalizedEmail,
                  requireText(displayName, "Display name is required."),
                  hashPassword(password, salt),
                  salt,
                  now,
                  now,
                  false);
          store.write(Path.of("users", user.id() + ".json"), user);
          log.info("Registered user awaiting email verification {}", user.email());
          return createEmailVerificationRequest(user);
        });
  }

  /** Creates an anonymous account with an unguessable internal credential and a session. */
  public AuthResult registerGuest() {
    Instant now = Instant.now();
    String id = UUID.randomUUID().toString();
    String salt = randomToken(24);
    UserRecord user =
        new UserRecord(
            id,
            "guest-" + id + "@guest.modriss.invalid",
            "Guest",
            hashPassword(randomToken(32), salt),
            salt,
            now,
            now);
    store.write(Path.of("users", user.id() + ".json"), user);
    log.info("Registered guest account {}", user.id());
    return issueSession(user);
  }

  /**
   * Verifies credentials and issues a fresh session.
   *
   * @param email user email address
   * @param password plain-text password to verify
   * @return issued authentication result
   */
  public AuthResult login(String email, String password) {
    UserRecord user = findByEmail(normalizeEmail(email));
    if (user == null
        || !constantTimeEquals(user.passwordHash(), hashPassword(password, user.salt()))) {
      throw new PlatformException(401, "Invalid email or password.");
    }
    if (!user.isEmailVerified()) {
      throw new PlatformException(
          403,
          "Verify your email address before signing in. Check your inbox or request a new"
              + " verification email.");
    }
    return issueSession(user);
  }

  /** Creates a fresh verification email request for an existing unverified account. */
  public Optional<EmailVerificationRequest> createEmailVerification(String email) {
    return store.inTransaction(
        () -> {
          UserRecord user = findByEmail(normalizeEmail(email));
          if (user == null || isGuest(user) || user.isEmailVerified()) {
            return Optional.empty();
          }
          return Optional.of(createEmailVerificationRequest(user));
        });
  }

  /** Marks an account verified using a valid, unexpired one-time token. */
  public void verifyEmail(String token) {
    store.inTransaction(
        () -> {
          String[] parts = token == null ? new String[0] : token.split("\\.", -1);
          if (parts.length != 2 || parts[1].isBlank()) {
            throw invalidEmailVerificationToken();
          }

          String id;
          try {
            id = UUID.fromString(parts[0]).toString();
          } catch (IllegalArgumentException ex) {
            throw invalidEmailVerificationToken();
          }

          Path tokenPath = Path.of("email-verifications", id + ".json");
          EmailVerificationToken verification =
              store.read(tokenPath, EmailVerificationToken.class).orElse(null);
          if (verification == null
              || !verification.expiresAt().isAfter(Instant.now())
              || !constantTimeEquals(verification.secretHash(), sha256Hex(parts[1]))) {
            throw invalidEmailVerificationToken();
          }

          Path userPath = Path.of("users", verification.userId() + ".json");
          UserRecord user =
              store
                  .read(userPath, UserRecord.class)
                  .orElseThrow(this::invalidEmailVerificationToken);
          if (isGuest(user)) {
            throw invalidEmailVerificationToken();
          }

          if (!user.isEmailVerified()) {
            store.write(
                userPath,
                new UserRecord(
                    user.id(),
                    user.email(),
                    user.displayName(),
                    user.passwordHash(),
                    user.salt(),
                    user.createdAt(),
                    Instant.now(),
                    true));
            log.info("Verified email address for user {}", user.id());
          }
          store.deleteIfExists(tokenPath);
          invalidateEmailVerificationTokens(user.id());
          return null;
        });
  }

  /**
   * Creates a short-lived password reset token for an existing, non-guest account.
   *
   * @param email account email address
   * @return reset email details when an eligible account exists
   */
  public Optional<PasswordResetRequest> createPasswordReset(String email) {
    return store.inTransaction(
        () -> {
          UserRecord user = findByEmail(normalizeEmail(email));
          if (user == null || user.email().endsWith("@guest.modriss.invalid")) {
            return Optional.empty();
          }

          invalidatePasswordResetTokens(user.id());
          Instant now = Instant.now();
          String id = UUID.randomUUID().toString();
          String secret = randomToken(32);
          store.write(
              Path.of("password-resets", id + ".json"),
              new PasswordResetToken(
                  id, user.id(), sha256Hex(secret), now.plus(PASSWORD_RESET_TTL)));
          return Optional.of(
              new PasswordResetRequest(user.email(), user.displayName(), id + "." + secret));
        });
  }

  /**
   * Replaces an account password using an unexpired reset token and revokes its active sessions.
   *
   * @param token emailed password reset token
   * @param password replacement password
   */
  public void resetPassword(String token, String password) {
    store.inTransaction(
        () -> {
          resetPasswordWithinTransaction(token, password);
          return null;
        });
  }

  private void resetPasswordWithinTransaction(String token, String password) {
    validatePassword(password);
    String[] parts = token == null ? new String[0] : token.split("\\.", -1);
    if (parts.length != 2 || parts[1].isBlank()) {
      throw invalidPasswordResetToken();
    }

    String id;
    try {
      id = UUID.fromString(parts[0]).toString();
    } catch (IllegalArgumentException ex) {
      throw invalidPasswordResetToken();
    }

    Path tokenPath = Path.of("password-resets", id + ".json");
    PasswordResetToken reset = store.read(tokenPath, PasswordResetToken.class).orElse(null);
    if (reset == null
        || reset.expiresAt().isBefore(Instant.now())
        || !constantTimeEquals(reset.secretHash(), sha256Hex(parts[1]))) {
      throw invalidPasswordResetToken();
    }

    Path userPath = Path.of("users", reset.userId() + ".json");
    UserRecord user =
        store.read(userPath, UserRecord.class).orElseThrow(this::invalidPasswordResetToken);
    if (user.email().endsWith("@guest.modriss.invalid")) {
      throw invalidPasswordResetToken();
    }

    String salt = randomToken(24);
    store.write(
        userPath,
        new UserRecord(
            user.id(),
            user.email(),
            user.displayName(),
            hashPassword(password, salt),
            salt,
            user.createdAt(),
            Instant.now(),
            user.isEmailVerified()));
    store.deleteIfExists(tokenPath);
    invalidatePasswordResetTokens(user.id());
    revokeSessions(user.id());
  }

  /**
   * Resolves a bearer token to an active user.
   *
   * @param token bearer token
   * @return authenticated user
   */
  public UserRecord requireUser(String token) {
    if (token == null || token.isBlank()) {
      throw new PlatformException(401, "Authentication token is required.");
    }
    String tokenHash = sessionTokenHash(token);
    AuthSession session =
        store
            .read(Path.of("sessions", tokenHash + ".json"), AuthSession.class)
            .orElseThrow(() -> new PlatformException(401, "Session is invalid or expired."));
    if (session.expiresAt().isBefore(Instant.now())) {
      logout(token);
      throw new PlatformException(401, "Session is invalid or expired.");
    }
    UserRecord user =
        store.require(
            Path.of("users", session.userId() + ".json"), UserRecord.class, "User not found.");
    if (!user.isEmailVerified()) {
      logout(token);
      throw new PlatformException(403, "Verify your email address before using this account.");
    }
    return user;
  }

  /**
   * Updates the authenticated user's display name.
   *
   * @param token bearer token
   * @param displayName replacement display name
   * @return updated user record
   */
  public UserRecord updateDisplayName(String token, String displayName) {
    UserRecord user = requireUser(token);
    UserRecord updated =
        new UserRecord(
            user.id(),
            user.email(),
            requireText(displayName, "Display name is required."),
            user.passwordHash(),
            user.salt(),
            user.createdAt(),
            Instant.now(),
            user.isEmailVerified());
    store.write(Path.of("users", user.id() + ".json"), updated);
    return updated;
  }

  /**
   * Removes a session token if it exists.
   *
   * @param token bearer token
   */
  public void logout(String token) {
    try {
      store.deleteIfExists(Path.of("sessions", sessionTokenHash(token) + ".json"));
    } catch (Exception ex) {
      log.warn("Could not remove session {}", shortToken(token), ex);
    }
  }

  /**
   * Finds a user by normalized email address.
   *
   * @param email email address to search
   * @return matching user, or {@code null} when absent
   */
  public UserRecord findByEmail(String email) {
    String normalized = normalizeEmail(email);
    return store.list(Path.of("users"), UserRecord.class).stream()
        .filter(user -> user.email().equals(normalized))
        .findFirst()
        .orElse(null);
  }

  /**
   * Lists all registered users.
   *
   * @return user records
   */
  public List<UserRecord> users() {
    return store.list(Path.of("users"), UserRecord.class);
  }

  /**
   * Creates and persists a new session for a user.
   *
   * @param user authenticated user
   * @return issued authentication result
   */
  private AuthResult issueSession(UserRecord user) {
    if (!user.isEmailVerified()) {
      throw new PlatformException(403, "Verify your email address before signing in.");
    }
    Instant now = Instant.now();
    String token = randomToken(32);
    String tokenHash = sessionTokenHash(token);
    AuthSession session = new AuthSession(tokenHash, user.id(), now, now.plus(sessionTtl));
    store.write(Path.of("sessions", tokenHash + ".json"), session);
    return new AuthResult(token, user);
  }

  private void invalidatePasswordResetTokens(String userId) {
    for (PasswordResetToken reset :
        store.list(Path.of("password-resets"), PasswordResetToken.class)) {
      if (userId.equals(reset.userId())) {
        store.deleteIfExists(Path.of("password-resets", reset.id() + ".json"));
      }
    }
  }

  private EmailVerificationRequest createEmailVerificationRequest(UserRecord user) {
    invalidateEmailVerificationTokens(user.id());
    Instant now = Instant.now();
    String id = UUID.randomUUID().toString();
    String secret = randomToken(32);
    store.write(
        Path.of("email-verifications", id + ".json"),
        new EmailVerificationToken(
            id, user.id(), sha256Hex(secret), now.plus(EMAIL_VERIFICATION_TTL)));
    return new EmailVerificationRequest(user.email(), user.displayName(), id + "." + secret);
  }

  private void invalidateEmailVerificationTokens(String userId) {
    for (EmailVerificationToken verification :
        store.list(Path.of("email-verifications"), EmailVerificationToken.class)) {
      if (userId.equals(verification.userId())) {
        store.deleteIfExists(Path.of("email-verifications", verification.id() + ".json"));
      }
    }
  }

  private boolean isGuest(UserRecord user) {
    return user.email().endsWith("@guest.modriss.invalid");
  }

  private void revokeSessions(String userId) {
    for (AuthSession session : store.list(Path.of("sessions"), AuthSession.class)) {
      if (userId.equals(session.userId())) {
        store.deleteIfExists(Path.of("sessions", session.token() + ".json"));
      }
    }
  }

  private PlatformException invalidPasswordResetToken() {
    return new PlatformException(400, "Password reset link is invalid or expired.");
  }

  private PlatformException invalidEmailVerificationToken() {
    return new PlatformException(400, "Email verification link is invalid or expired.");
  }

  private String sessionTokenHash(String token) {
    if (token == null || token.isBlank()) {
      throw new PlatformException(401, "Authentication token is required.");
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new PlatformException(500, "Could not process session token.");
    }
  }

  private String sha256Hex(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new PlatformException(500, "Could not process password reset token.");
    }
  }

  /**
   * Hashes a password with PBKDF2 and the stored salt.
   *
   * @param password plain-text password
   * @param salt URL-safe Base64 salt
   * @return hexadecimal password hash
   */
  private String hashPassword(String password, String salt) {
    try {
      PBEKeySpec spec =
          new PBEKeySpec(
              password.toCharArray(), Base64.getUrlDecoder().decode(salt), ITERATIONS, KEY_BITS);
      return HexFormat.of()
          .formatHex(
              SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                  .generateSecret(spec)
                  .getEncoded());
    } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
      throw new PlatformException(500, "Could not process credentials.");
    }
  }

  /**
   * Generates a URL-safe random token.
   *
   * @param bytes number of random bytes before encoding
   * @return URL-safe token without padding
   */
  private String randomToken(int bytes) {
    byte[] data = new byte[bytes];
    random.nextBytes(data);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
  }

  /**
   * Compares two UTF-8 strings using {@link MessageDigest#isEqual(byte[], byte[])}.
   *
   * @param left first value
   * @param right second value
   * @return {@code true} when values match
   */
  private boolean constantTimeEquals(String left, String right) {
    return MessageDigest.isEqual(
        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Validates and lowercases an email address.
   *
   * @param email email address
   * @return normalized email
   */
  private String normalizeEmail(String email) {
    String value = requireText(email, "Email is required.").toLowerCase(Locale.ROOT);
    if (!value.contains("@")) {
      throw new PlatformException(400, "Enter a valid email address.");
    }
    return value;
  }

  /**
   * Enforces minimum password requirements.
   *
   * @param password password to validate
   */
  private void validatePassword(String password) {
    if (password == null || password.length() < 8) {
      throw new PlatformException(400, "Password must be at least 8 characters.");
    }
  }

  /**
   * Returns trimmed text or raises a platform validation error.
   *
   * @param value raw text value
   * @param message validation error message
   * @return trimmed text
   */
  private String requireText(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new PlatformException(400, message);
    }
    return value.trim();
  }

  /**
   * Returns a short token prefix for logs.
   *
   * @param token full token
   * @return short token representation
   */
  private String shortToken(String token) {
    return token == null || token.length() < 8 ? "<empty>" : token.substring(0, 8);
  }

  /**
   * Authentication response containing a bearer token and user record.
   *
   * @param token issued bearer token
   * @param user authenticated user
   */
  public record AuthResult(String token, UserRecord user) {}

  /** Details used only to deliver a password reset email. */
  public record PasswordResetRequest(String email, String displayName, String token) {}

  /** Details used only to deliver an email verification link. */
  public record EmailVerificationRequest(String email, String displayName, String token) {}
}
