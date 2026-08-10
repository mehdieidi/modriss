package io.mehdieidi.varka.platform.assistant.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Loads focused modeling skills from immutable application resources. */
public final class AssistantSkills {
  private static final String ROOT = "skills/assistant/";
  private static final Map<String, Skill> CACHE = new ConcurrentHashMap<>();

  private AssistantSkills() {}

  /** Returns one validated skill with stable audit metadata. */
  public static Skill load(String name) {
    return CACHE.computeIfAbsent(name, AssistantSkills::read);
  }

  /** Renders only the skills needed by the current workflow phase. */
  public static String prompt(String... names) {
    List<Skill> skills =
        Arrays.stream(names)
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .map(AssistantSkills::load)
            .toList();
    if (skills.isEmpty()) return "";
    return skills.stream()
        .map(
            skill ->
                "<assistant-skill name=\""
                    + skill.name()
                    + "\" hash=\""
                    + skill.hash().substring(0, 12)
                    + "\">\n"
                    + skill.instructions()
                    + "\n</assistant-skill>")
        .collect(Collectors.joining("\n\n"));
  }

  private static Skill read(String requestedName) {
    String resource = ROOT + requestedName + "/SKILL.md";
    try (InputStream input = AssistantSkills.class.getClassLoader().getResourceAsStream(resource)) {
      if (input == null)
        throw new IllegalArgumentException("Missing assistant skill: " + requestedName);
      String source = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
      Parsed parsed = parse(source);
      if (!requestedName.equals(parsed.name())) {
        throw new IllegalArgumentException(
            "Assistant skill folder and frontmatter name differ: " + requestedName);
      }
      if (parsed.description().isBlank() || parsed.instructions().isBlank()) {
        throw new IllegalArgumentException("Assistant skill is incomplete: " + requestedName);
      }
      return new Skill(
          parsed.name(), parsed.description(), parsed.instructions(), sha256(source), resource);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to load assistant skill: " + requestedName, ex);
    }
  }

  private static Parsed parse(String source) {
    if (!source.startsWith("---\n")) {
      throw new IllegalArgumentException("Assistant skill needs YAML frontmatter.");
    }
    int end = source.indexOf("\n---\n", 4);
    if (end < 0) throw new IllegalArgumentException("Assistant skill frontmatter is not closed.");
    String name = "";
    String description = "";
    for (String line : source.substring(4, end).lines().toList()) {
      int separator = line.indexOf(':');
      if (separator < 0) continue;
      String key = line.substring(0, separator).trim();
      String value = line.substring(separator + 1).trim();
      if ("name".equals(key)) name = value;
      if ("description".equals(key)) description = value;
    }
    return new Parsed(name, description, source.substring(end + 5).trim());
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

  /** Immutable packaged skill. */
  public record Skill(
      String name, String description, String instructions, String hash, String resource) {}

  private record Parsed(String name, String description, String instructions) {}
}
