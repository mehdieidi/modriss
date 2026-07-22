package io.mehdieidi.varka.platform.assistant.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loads immutable, versioned assistant prompt templates from the application resources. */
public final class PromptTemplates {
  private static final String ROOT = "prompts/assistant/";
  private static final Map<String, Template> CACHE = new ConcurrentHashMap<>();

  private PromptTemplates() {}

  /** Returns the named prompt along with its stable content hash for provider auditing. */
  public static Template load(String name) {
    return CACHE.computeIfAbsent(name, PromptTemplates::read);
  }

  private static Template read(String name) {
    String resource = ROOT + name + ".md";
    try (InputStream input = PromptTemplates.class.getClassLoader().getResourceAsStream(resource)) {
      if (input == null)
        throw new IllegalArgumentException("Missing assistant prompt template: " + name);
      String body = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
      String version =
          body.lines()
              .findFirst()
              .filter(line -> line.startsWith("version:"))
              .map(line -> line.substring("version:".length()).trim())
              .orElse("unversioned");
      return new Template(name, version, body, sha256(body));
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to load assistant prompt template: " + name, ex);
    }
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) hex.append(String.format("%02x", b));
      return hex.toString();
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** Immutable prompt metadata. */
  public record Template(String name, String version, String body, String hash) {}
}
