package io.mehdieidi.modriss.platform.identity.domain;

import java.time.Instant;

/**
 * Persisted platform user account.
 *
 * @param id user identifier
 * @param email normalized email address
 * @param displayName display name
 * @param passwordHash password hash
 * @param salt password salt
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 * @param emailVerified whether the user has verified their email address; null legacy values mean
 *     verified
 */
public record UserRecord(
    String id,
    String email,
    String displayName,
    String passwordHash,
    String salt,
    Instant createdAt,
    Instant updatedAt,
    Boolean emailVerified) {

  public UserRecord {
    if (emailVerified == null) {
      emailVerified = true;
    }
  }

  /** Creates a verified user record for trusted internal account creation and legacy callers. */
  public UserRecord(
      String id,
      String email,
      String displayName,
      String passwordHash,
      String salt,
      Instant createdAt,
      Instant updatedAt) {
    this(id, email, displayName, passwordHash, salt, createdAt, updatedAt, true);
  }

  /** Treats records written before email verification existed as verified. */
  public boolean isEmailVerified() {
    return !Boolean.FALSE.equals(emailVerified);
  }
}
