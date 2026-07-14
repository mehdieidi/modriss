package io.mehdieidi.varka.platform.identity.application;

import io.mehdieidi.varka.platform.identity.domain.AuthSession;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.storage.api.PlatformStore;
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
   * Registers a new user and immediately issues a session.
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
    return issueSession(user);
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
    return store.require(
        Path.of("users", session.userId() + ".json"), UserRecord.class, "User not found.");
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
            Instant.now());
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
    Instant now = Instant.now();
    String token = randomToken(32);
    String tokenHash = sessionTokenHash(token);
    AuthSession session = new AuthSession(tokenHash, user.id(), now, now.plus(sessionTtl));
    store.write(Path.of("sessions", tokenHash + ".json"), session);
    return new AuthResult(token, user);
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
}
