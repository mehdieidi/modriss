package io.mehdieidi.modriss.platform.assistant.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads repository and module `.env` files for opt-in integration tests. */
public final class TestEnvFiles {

  private TestEnvFiles() {}

  public static Map<String, String> load() {
    Map<String, String> values = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
      values.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue());
    }
    for (String key : System.getProperties().stringPropertyNames()) {
      if (key == null || key.isBlank()) {
        continue;
      }
      String value = System.getProperty(key);
      if (value != null) {
        values.put(key.toUpperCase(Locale.ROOT), value);
      }
    }
    for (Path envFile : List.of(Path.of(".env"), Path.of("../../../.env").normalize())) {
      if (!Files.exists(envFile)) {
        continue;
      }
      try {
        for (String rawLine : Files.readAllLines(envFile)) {
          String line = rawLine.trim();
          if (line.isBlank() || line.startsWith("#") || !line.contains("=")) {
            continue;
          }
          String[] parts = line.split("=", 2);
          values.putIfAbsent(parts[0].trim().toUpperCase(Locale.ROOT), unquote(parts[1].trim()));
        }
      } catch (Exception ignored) {
        // Environment variables are still enough for the opt-in test.
      }
    }
    return values;
  }

  public static String get(String key) {
    if (key == null || key.isBlank()) {
      return "";
    }
    return load().getOrDefault(key.toUpperCase(Locale.ROOT), "");
  }

  private static String unquote(String value) {
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }
}
