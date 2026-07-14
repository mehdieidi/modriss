package io.mehdieidi.varka.backend.admin;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the protected administration workspace. */
@ConfigurationProperties(prefix = "varka.admin")
public record AdminProperties(
    boolean bootstrapEnabled, List<String> bootstrapEmails, String bootstrapToken) {

  /** Returns whether one-time administrator bootstrap is enabled. */
  public boolean bootstrapEnabled() {
    return bootstrapEnabled;
  }

  /** Returns configured bootstrap administrator emails. */
  public List<String> bootstrapEmails() {
    return bootstrapEmails == null ? List.of() : bootstrapEmails;
  }

  /** Returns the configured one-time bootstrap token. */
  public String bootstrapToken() {
    return bootstrapToken == null ? "" : bootstrapToken.trim();
  }
}
