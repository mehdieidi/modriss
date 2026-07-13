package io.mehdieidi.varka.backend.api.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

/** Compares Spring MVC handler mappings with the checked-in OpenAPI specification. */
final class OpenApiContractSupport {

  private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^}:]+)(?::[^}]+)?\\}");
  private static final Set<String> EXCLUDED_PREFIXES =
      Set.of("/actuator", "/error", "/v3/api-docs", "/swagger-ui");

  private OpenApiContractSupport() {}

  /**
   * Loads API operations declared in the repository OpenAPI document.
   *
   * @return normalized path and HTTP method pairs
   * @throws IOException if the specification cannot be read
   */
  @SuppressWarnings("unchecked")
  static Set<String> loadOpenApiOperations() throws IOException {
    Path specPath = resolveOpenApiSpec();
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Map<String, Object> document = mapper.readValue(specPath.toFile(), Map.class);
    Map<String, Object> paths = (Map<String, Object>) document.get("paths");

    Set<String> operations = new LinkedHashSet<>();
    for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
      String path = normalizePath(pathEntry.getKey());
      if (path.startsWith("/ws/")) {
        continue;
      }
      Map<String, Object> methods = (Map<String, Object>) pathEntry.getValue();
      for (String method : methods.keySet()) {
        if (isHttpMethod(method)) {
          operations.add(operationKey(method, path));
        }
      }
    }
    return operations;
  }

  /**
   * Collects API operations exposed by Spring MVC controllers.
   *
   * @param handlerMapping registered request mappings
   * @return normalized path and HTTP method pairs
   */
  static Set<String> loadSpringOperations(RequestMappingHandlerMapping handlerMapping) {
    Set<String> operations = new LinkedHashSet<>();
    for (Map.Entry<RequestMappingInfo, ?> entry : handlerMapping.getHandlerMethods().entrySet()) {
      RequestMappingInfo mapping = entry.getKey();
      for (String pattern : mapping.getPatternValues()) {
        Set<String> methods =
            mapping.getMethodsCondition().getMethods().stream()
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (methods.isEmpty()) {
          methods = new LinkedHashSet<>(defaultMethodsForPattern(pattern));
        }

        String normalized = normalizePath(pattern);
        if (isExcluded(normalized)) {
          continue;
        }
        if (!normalized.startsWith("/api/")) {
          continue;
        }
        for (String method : methods) {
          operations.add(operationKey(method, normalized));
        }
      }
    }
    return operations;
  }

  /**
   * Formats contract mismatches for readable assertion output.
   *
   * @param title mismatch section title
   * @param operations missing operations
   * @return formatted multiline message
   */
  static String formatMismatch(String title, Set<String> operations) {
    String listing =
        new TreeSet<>(operations)
            .stream().collect(Collectors.joining(System.lineSeparator() + "  "));
    return title + " (" + operations.size() + "):" + System.lineSeparator() + "  " + listing;
  }

  private static Path resolveOpenApiSpec() {
    Path fromModule = Path.of("..", "..", "docs", "api", "openapi", "openapi.yaml");
    Path fromRoot = Path.of("docs", "api", "openapi", "openapi.yaml");
    if (Files.exists(fromModule)) {
      return fromModule.normalize();
    }
    if (Files.exists(fromRoot)) {
      return fromRoot.normalize();
    }
    throw new IllegalStateException(
        "OpenAPI specification not found at docs/api/openapi/openapi.yaml");
  }

  private static boolean isHttpMethod(String value) {
    return switch (value.toUpperCase()) {
      case "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS" -> true;
      default -> false;
    };
  }

  private static boolean isExcluded(String path) {
    return EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith);
  }

  private static String operationKey(String method, String path) {
    return method.toUpperCase() + " " + path;
  }

  private static Set<String> defaultMethodsForPattern(String pattern) {
    String normalized = normalizePath(pattern);
    if (normalized.startsWith("/api/impact/") || normalized.startsWith("/api/admin/")) {
      return Set.of("GET", "POST");
    }
    return Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
  }

  private static String normalizePath(String path) {
    String normalized = path.trim();
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    normalized = normalized.replaceAll("/\\*\\*$", "/{path}");
    normalized = normalized.replace("/**", "/{path}");
    normalized = normalized.replace("/*", "/{segment}");
    normalized = PATH_VARIABLE_PATTERN.matcher(normalized).replaceAll("{$1}");
    if (normalized.endsWith("/") && normalized.length() > 1) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
