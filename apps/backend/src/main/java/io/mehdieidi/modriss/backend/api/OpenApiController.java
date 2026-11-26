package io.mehdieidi.modriss.backend.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

/** Serves the local OpenAPI specification and its browser-based Swagger UI. */
@RestController
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", matchIfMissing = true)
public class OpenApiController {

  private static final ObjectMapper OPENAPI_YAML = new ObjectMapper(new YAMLFactory());
  private static final Path OPENAPI_SPEC = Path.of(".idea", "docs", "api", "openapi", "openapi.yaml");

  /** Finds the private specification when running from the repository root or backend module. */
  public static Path findLocalSpec() {
    return Files.isRegularFile(OPENAPI_SPEC)
        ? OPENAPI_SPEC
        : Path.of("..", "..").resolve(OPENAPI_SPEC);
  }

  private static Path requireLocalSpec() {
    Path spec = findLocalSpec();
    if (!Files.isRegularFile(spec)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API documentation is disabled.");
    }
    return spec;
  }

  /**
   * Loads the complete local OpenAPI document.
   *
   * @return OpenAPI document
   */
  @GetMapping("/v3/api-docs")
  @SuppressWarnings("unchecked")
  Map<String, Object> docs() {
    try (InputStream input = Files.newInputStream(requireLocalSpec())) {
      return (Map<String, Object>) OPENAPI_YAML.readValue(input, Map.class);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not load the OpenAPI specification.", exception);
    }
  }

  /**
   * Serves a Swagger UI configured to load the generated OpenAPI document.
   *
   * @return Swagger UI HTML page
   */
  @GetMapping(value = "/swagger-ui.html", produces = MediaType.TEXT_HTML_VALUE)
  String swaggerUi() {
    requireLocalSpec();
    return """
    <!doctype html>
    <html>
    <head>
      <title>MODRISS API Docs</title>
      <link rel="stylesheet" href="/webjars/swagger-ui/5.33.2/swagger-ui.css">
    </head>
    <body>
      <div id="swagger-ui"></div>
      <script src="/webjars/swagger-ui/5.33.2/swagger-ui-bundle.js"></script>
      <script src="/swagger-ui-init.js"></script>
    </body>
    </html>
    """;
  }
}
