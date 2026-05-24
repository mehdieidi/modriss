package io.mehdieidi.modless.platform.core.service;

import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.AuthSession;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private final JsonFileStore store;
    private final SecureRandom random = new SecureRandom();
    private final Duration sessionTtl;

    public AuthService(JsonFileStore store, Duration sessionTtl) {
        this.store = store;
        this.sessionTtl = sessionTtl;
    }

    public AuthResult register(String email, String password, String displayName) {
        String normalizedEmail = normalizeEmail(email);
        validatePassword(password);
        if (findByEmail(normalizedEmail) != null) {
            throw new PlatformException(409, "An account with this email already exists.");
        }
        Instant now = Instant.now();
        String salt = randomToken(24);
        UserRecord user = new UserRecord(
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

    public AuthResult login(String email, String password) {
        UserRecord user = findByEmail(normalizeEmail(email));
        if (user == null || !constantTimeEquals(user.passwordHash(),
                hashPassword(password, user.salt()))) {
            throw new PlatformException(401, "Invalid email or password.");
        }
        return issueSession(user);
    }

    public UserRecord requireUser(String token) {
        if (token == null || token.isBlank()) {
            throw new PlatformException(401, "Authentication token is required.");
        }
        AuthSession session = store.read(Path.of("sessions", token + ".json"), AuthSession.class)
                .orElseThrow(() -> new PlatformException(401, "Session is invalid or expired."));
        if (session.expiresAt().isBefore(Instant.now())) {
            logout(token);
            throw new PlatformException(401, "Session is invalid or expired.");
        }
        return store.require(Path.of("users", session.userId() + ".json"), UserRecord.class,
                "User not found.");
    }

    public UserRecord updateDisplayName(String token, String displayName) {
        UserRecord user = requireUser(token);
        UserRecord updated = new UserRecord(user.id(), user.email(), requireText(displayName,
                "Display name is required."), user.passwordHash(), user.salt(), user.createdAt(),
                Instant.now());
        store.write(Path.of("users", user.id() + ".json"), updated);
        return updated;
    }

    public void logout(String token) {
        try {
            Files.deleteIfExists(store.resolve(Path.of("sessions", token + ".json")));
        } catch (Exception ex) {
            log.warn("Could not remove session {}", shortToken(token), ex);
        }
    }

    public UserRecord findByEmail(String email) {
        String normalized = normalizeEmail(email);
        Path users = store.resolve(Path.of("users"));
        try (Stream<Path> files = Files.list(users)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> store.read(store.root().relativize(path), UserRecord.class)
                            .orElse(null))
                    .filter(user -> user != null && user.email().equals(normalized))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not read user store.");
        }
    }

    public List<UserRecord> users() {
        try (Stream<Path> files = Files.list(store.resolve(Path.of("users")))) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> store.read(store.root().relativize(path), UserRecord.class)
                            .orElse(null))
                    .filter(user -> user != null)
                    .toList();
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not read user store.");
        }
    }

    private AuthResult issueSession(UserRecord user) {
        Instant now = Instant.now();
        String token = randomToken(32);
        AuthSession session = new AuthSession(token, user.id(), now, now.plus(sessionTtl));
        store.write(Path.of("sessions", token + ".json"), session);
        return new AuthResult(token, user);
    }

    private String hashPassword(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),
                    Base64.getUrlDecoder().decode(salt), ITERATIONS, KEY_BITS);
            return HexFormat.of().formatHex(
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
                            .getEncoded());
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not process credentials.");
        }
    }

    private String randomToken(int bytes) {
        byte[] data = new byte[bytes];
        random.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeEmail(String email) {
        String value = requireText(email, "Email is required.").toLowerCase(Locale.ROOT);
        if (!value.contains("@")) {
            throw new PlatformException(400, "Enter a valid email address.");
        }
        return value;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new PlatformException(400, "Password must be at least 8 characters.");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new PlatformException(400, message);
        }
        return value.trim();
    }

    private String shortToken(String token) {
        return token == null || token.length() < 8 ? "<empty>" : token.substring(0, 8);
    }

    public record AuthResult(String token, UserRecord user) {

    }
}
