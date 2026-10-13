package io.mehdieidi.modriss.backend.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

/** Serves the repository OpenAPI specification and a browser-based Swagger UI. */
@RestController
public class OpenApiController {

  private static final ObjectMapper OPENAPI_YAML = new ObjectMapper(new YAMLFactory());

  /**
   * Loads the complete, checked-in OpenAPI document.
   *
   * @return OpenAPI document
   */
  @GetMapping("/v3/api-docs")
  @SuppressWarnings("unchecked")
  Map<String, Object> docs() {
    try (InputStream input = new ClassPathResource("static/openapi.yaml").getInputStream()) {
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
