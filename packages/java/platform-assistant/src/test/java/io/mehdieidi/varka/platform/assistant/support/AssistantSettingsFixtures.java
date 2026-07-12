package io.mehdieidi.varka.platform.assistant.support;

import io.mehdieidi.varka.platform.assistant.spi.AssistantSettings;
import java.time.Duration;

/** Test doubles for assistant settings. */
public final class AssistantSettingsFixtures {

  private AssistantSettingsFixtures() {}

  public static AssistantSettings defaults() {
    return settings(6, 24, 96, 2400, 14000, 10);
  }

  public static AssistantSettings settings(
      int validationRepairAttempts,
      int maxContextSnippets,
      int maxToolCalls,
      int maxSnippetChars,
      int maxSystemChars,
      int reservedSchemaSnippets) {
    return new AssistantSettings() {
      @Override
      public boolean enabled() {
        return true;
      }

      @Override
      public int validationRepairAttempts() {
        return validationRepairAttempts;
      }

      @Override
      public int maxContextSnippets() {
        return maxContextSnippets;
      }

      @Override
      public int maxToolCalls() {
        return maxToolCalls;
      }

      @Override
      public int maxSnippetChars() {
        return maxSnippetChars;
      }

      @Override
      public int maxSystemChars() {
        return maxSystemChars;
      }

      @Override
      public int reservedSchemaSnippets() {
        return reservedSchemaSnippets;
      }

      @Override
      public Duration requestTimeout() {
        return Duration.ofMinutes(10);
      }

      @Override
      public Hardening hardening() {
        return new Hardening() {
          @Override
          public int perUserRequestsPerWindow() {
            return 30;
          }

          @Override
          public Duration rateLimitWindow() {
            return Duration.ofMinutes(1);
          }

          @Override
          public int circuitFailureThreshold() {
            return 3;
          }

          @Override
          public Duration circuitOpenDuration() {
            return Duration.ofMinutes(1);
          }

          @Override
          public int providerRetryAttempts() {
            return 2;
          }

          @Override
          public Duration retryBackoff() {
            return Duration.ofMillis(250);
          }

          @Override
          public int recentMessageWindow() {
            return 12;
          }
        };
      }
    };
  }

  public static AssistantSettings withHardening(
      int perUserRequestsPerWindow,
      Duration rateLimitWindow,
      int circuitFailureThreshold,
      Duration circuitOpenDuration,
      int providerRetryAttempts,
      Duration retryBackoff,
      int recentMessageWindow) {
    AssistantSettings defaults = defaults();
    return new AssistantSettings() {
      @Override
      public boolean enabled() {
        return defaults.enabled();
      }

      @Override
      public int validationRepairAttempts() {
        return defaults.validationRepairAttempts();
      }

      @Override
      public int maxContextSnippets() {
        return defaults.maxContextSnippets();
      }

      @Override
      public int maxToolCalls() {
        return defaults.maxToolCalls();
      }

      @Override
      public int maxSnippetChars() {
        return defaults.maxSnippetChars();
      }

      @Override
      public int maxSystemChars() {
        return defaults.maxSystemChars();
      }

      @Override
      public int reservedSchemaSnippets() {
        return defaults.reservedSchemaSnippets();
      }

      @Override
      public Duration requestTimeout() {
        return defaults.requestTimeout();
      }

      @Override
      public Hardening hardening() {
        return new Hardening() {
          @Override
          public int perUserRequestsPerWindow() {
            return perUserRequestsPerWindow;
          }

          @Override
          public Duration rateLimitWindow() {
            return rateLimitWindow;
          }

          @Override
          public int circuitFailureThreshold() {
            return circuitFailureThreshold;
          }

          @Override
          public Duration circuitOpenDuration() {
            return circuitOpenDuration;
          }

          @Override
          public int providerRetryAttempts() {
            return providerRetryAttempts;
          }

          @Override
          public Duration retryBackoff() {
            return retryBackoff;
          }

          @Override
          public int recentMessageWindow() {
            return recentMessageWindow;
          }
        };
      }
    };
  }
}
