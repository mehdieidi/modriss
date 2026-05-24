package io.mehdieidi.modless.platform.core.model;

import java.time.Instant;

public record AuthSession(String token, String userId, Instant createdAt, Instant expiresAt) {

}
