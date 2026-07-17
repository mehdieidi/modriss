package io.mehdieidi.varka.backend.guest;

import io.mehdieidi.varka.backend.config.BackendProperties;
import io.mehdieidi.varka.platform.identity.application.AuthService;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates anonymous accounts and enforces their persisted assistant prompt allowance. */
@Service
public class GuestAccessService {

  private final AuthService auth;
  private final JdbcTemplate jdbc;
  private final int promptLimit;

  public GuestAccessService(AuthService auth, JdbcTemplate jdbc, BackendProperties properties) {
    this.auth = auth;
    this.jdbc = jdbc;
    this.promptLimit = properties.guest().promptLimit();
  }

  /** Creates an isolated guest identity and records its fixed allowance. */
  @Transactional
  public AuthService.AuthResult createGuest() {
    AuthService.AuthResult result = auth.registerGuest();
    jdbc.update(
        "INSERT INTO guest_accounts(user_id, prompt_limit, prompts_used, created_at) VALUES (?, ?,"
            + " 0, ?)",
        result.user().id(),
        promptLimit,
        Timestamp.from(Instant.now()));
    return result;
  }

  /** Returns whether an account is an anonymous guest. */
  public boolean isGuest(String userId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM guest_accounts WHERE user_id = ?", Integer.class, userId);
    return count != null && count > 0;
  }

  /** Atomically reserves one assistant prompt, rejecting exhausted guest accounts. */
  public void consumePrompt(UserRecord user) {
    int changed =
        jdbc.update(
            "UPDATE guest_accounts SET prompts_used = prompts_used + 1 WHERE user_id = ? AND"
                + " prompts_used < prompt_limit",
            user.id());
    if (isGuest(user.id()) && changed == 0) {
      throw new PlatformException(
          403, "Guest prompt limit reached. Log in or register to continue.");
    }
  }
}
