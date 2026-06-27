package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import java.util.List;
import java.util.regex.Pattern;

/** Redacts secrets and bounds untrusted text before it reaches an AI provider. */
public class AssistantPromptGuard {

  private static final int MAX_USER_CHARS = 4000;
  private static final Pattern SECRET =
      Pattern.compile(
          "(?i)(api[-_ ]?key|authorization|password|secret|token)\\s*[:=]\\s*" + "([^\\s,;]+)");
  private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/-]+");
  private static final Pattern INJECTION =
      Pattern.compile(
          "(?i)(ignore|override|disregard)\\s+(all\\s+)?(previous|prior|system)"
              + "\\s+(instructions|prompt)");

  private final AssistantSettings properties;

  public AssistantPromptGuard(AssistantSettings properties) {
    this.properties = properties;
  }

  /**
   * Sanitizes one provider prompt while preserving compact trusted context.
   *
   * @param prompt raw prompt
   * @return sanitized prompt
   */
  public AssistantModelProvider.AssistantPrompt sanitize(
      AssistantModelProvider.AssistantPrompt prompt) {
    String user = bound(redact(prompt.user()), MAX_USER_CHARS);
    if (INJECTION.matcher(user).find()) {
      user = "[Potential prompt-injection text treated as untrusted user content]\n" + user;
    }
    boolean fullContext = prompt.snippets().stream().anyMatch(this::isFullContextSnippet);
    List<AssistantModelProvider.ContextSnippet> snippets =
        prompt.snippets().stream()
            .limit(fullContext ? Long.MAX_VALUE : properties.maxContextSnippets())
            .map(
                snippet ->
                    new AssistantModelProvider.ContextSnippet(
                        bound(redact(snippet.source()), 300),
                        bound(redact(snippet.title()), 300),
                        bound(redact(snippet.content()), snippetLimit(snippet, fullContext))))
            .toList();
    int maxSystemChars =
        fullContext
            ? Integer.MAX_VALUE
            : prompt.system().contains("Attached context file:")
                ? Math.max(properties.maxSystemChars(), 32000)
                : properties.maxSystemChars();
    return new AssistantModelProvider.AssistantPrompt(
        prompt.role(), bound(redact(prompt.system()), maxSystemChars), user, snippets);
  }

  /**
   * Redacts common credential forms.
   *
   * @param value untrusted text
   * @return redacted text
   */
  public String redact(String value) {
    String safe = value == null ? "" : value;
    safe = BEARER.matcher(safe).replaceAll("Bearer [REDACTED]");
    safe = SECRET.matcher(safe).replaceAll("$1=[REDACTED]");
    return safe;
  }

  private String bound(String value, int maxChars) {
    return maxChars == Integer.MAX_VALUE || value.length() <= maxChars
        ? value
        : value.substring(0, maxChars) + "\n[truncated by backend]";
  }

  private int snippetLimit(AssistantModelProvider.ContextSnippet snippet, boolean fullContext) {
    if (fullContext || isFullContextSnippet(snippet)) {
      return Integer.MAX_VALUE;
    }
    String source = snippet.source() == null ? "" : snippet.source();
    if (source.startsWith("user-attachment")) {
      return Math.max(properties.maxSnippetChars(), 24000);
    }
    return properties.maxSnippetChars();
  }

  private boolean isFullContextSnippet(AssistantModelProvider.ContextSnippet snippet) {
    return snippet != null
        && snippet.source() != null
        && snippet.source().startsWith("full-context");
  }
}
