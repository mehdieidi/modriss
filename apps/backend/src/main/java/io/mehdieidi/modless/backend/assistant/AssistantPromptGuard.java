package io.mehdieidi.modless.backend.assistant;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Redacts secrets and bounds untrusted text before it reaches an AI provider.
 */
@Component
public class AssistantPromptGuard {

    private static final int MAX_USER_CHARS = 4000;
    private static final int MAX_SYSTEM_CHARS = 12000;
    private static final int MAX_SNIPPET_CHARS = 1800;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(api[-_ ]?key|authorization|password|secret|token)\\s*[:=]\\s*"
                    + "([^\\s,;]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/-]+");
    private static final Pattern INJECTION = Pattern.compile(
            "(?i)(ignore|override|disregard)\\s+(all\\s+)?(previous|prior|system)"
                    + "\\s+(instructions|prompt)");

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
        List<AssistantModelProvider.ContextSnippet> snippets = prompt.snippets().stream()
                .limit(8)
                .map(snippet -> new AssistantModelProvider.ContextSnippet(
                        bound(redact(snippet.source()), 300),
                        bound(redact(snippet.title()), 300),
                        bound(redact(snippet.content()), MAX_SNIPPET_CHARS)))
                .toList();
        return new AssistantModelProvider.AssistantPrompt(prompt.role(),
                bound(redact(prompt.system()), MAX_SYSTEM_CHARS), user, snippets);
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
        return value.length() <= maxChars ? value : value.substring(0, maxChars)
                + "\n[truncated by backend]";
    }
}
