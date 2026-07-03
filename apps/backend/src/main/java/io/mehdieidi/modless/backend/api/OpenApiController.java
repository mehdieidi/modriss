package io.mehdieidi.modless.backend.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves a lightweight OpenAPI description and a browser-based Swagger UI. */
@RestController
public class OpenApiController {

  /**
   * Builds the OpenAPI path summary exposed by this backend.
   *
   * @return OpenAPI document
   */
  @GetMapping("/v3/api-docs")
  Map<String, Object> docs() {
    Map<String, Object> paths = new LinkedHashMap<>();
    add(paths, "/api/auth/register", "post", "Register a user");
    add(paths, "/api/auth/login", "post", "Create an auth session");
    add(paths, "/api/auth/me", "get", "Get current user");
    add(paths, "/api/projects", "get", "List projects");
    add(paths, "/api/projects", "post", "Create project");
    add(paths, "/api/projects/{id}", "get", "Get project");
    add(paths, "/api/projects/{id}", "put", "Update project");
    add(paths, "/api/projects/{id}", "delete", "Delete project");
    add(paths, "/api/projects/{id}/download", "get", "Download project ZIP");
    add(paths, "/api/{level}", "get", "List CIM/PIM/PSM models");
    add(paths, "/api/{level}", "post", "Create CIM/PIM/PSM model");
    add(paths, "/api/{level}/{id}", "get", "Get model");
    add(paths, "/api/{level}/{id}", "put", "Update model");
    add(paths, "/api/{level}/{id}", "patch", "Patch model with small change operations");
    add(paths, "/api/{level}/validate", "post", "Validate model");
    add(paths, "/api/{level}/{id}/validate", "post", "Validate stored model");
    add(paths, "/api/{level}/import", "post", "Import model");
    add(paths, "/api/{level}/export", "post", "Export model");
    add(paths, "/api/{level}/{id}/export", "post", "Export stored model");
    add(paths, "/api/transformations/cim-to-pim", "post", "Generate PIM from CIM");
    add(paths, "/api/transformations/pim-to-psm", "post", "Generate PSM from PIM");
    add(paths, "/api/transformations/psm-to-artifact", "post", "Generate artifacts from PSM");
    add(paths, "/api/artifact", "get", "List artifacts");
    add(paths, "/api/artifact/{id}", "get", "Get artifact");
    add(paths, "/api/artifact/{id}/file", "get", "Read artifact file");
    add(paths, "/api/artifact/{id}/files", "put", "Save artifact file");
    add(paths, "/api/artifact/{id}/download", "get", "Download artifact ZIP");
    add(paths, "/api/modeling/config", "get", "Get modeling palette/configuration");
    add(paths, "/api/modeling/process/{level}", "get", "Get modeling process definition");
    add(
        paths,
        "/api/modeling/process/{level}/coverage",
        "get",
        "Get modeling concept coverage matrix");
    add(paths, "/api/layout", "post", "Auto-layout diagram nodes");
    add(paths, "/api/chatbot/sessions", "post", "Create assistant session");
    add(paths, "/api/chatbot/sessions/{sessionId}", "delete", "Clear assistant session memory");
    add(paths, "/api/chatbot/sessions/{sessionId}/cancel", "post", "Cancel active assistant turn");
    add(paths, "/api/chatbot/sessions/{sessionId}/messages", "post", "Send assistant message");
    add(
        paths,
        "/api/chatbot/sessions/{sessionId}/events",
        "get",
        "Open assistant SSE event stream");
    add(
        paths,
        "/api/chatbot/sessions/{sessionId}/proposals/{proposalId}",
        "get",
        "Get assistant proposal");
    add(
        paths,
        "/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/undo",
        "post",
        "Undo applied assistant proposal");
    add(paths, "/api/chatbot/sessions/{sessionId}/choices", "post", "Submit assistant choice");
    add(paths, "/ws/chatbot/sessions/{sessionId}", "get", "Open assistant WebSocket stream");
    add(paths, "/api/health", "get", "Get backend health");
    return Map.of(
        "openapi",
        "3.1.0",
        "info",
        Map.of("title", "Modless Backend API", "version", "0.0.1"),
        "paths",
        paths);
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
      <title>Modless API Docs</title>
      <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
    </head>
    <body>
      <div id="swagger-ui"></div>
      <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
      <script>
        window.onload = () => SwaggerUIBundle({ url: '/v3/api-docs', dom_id: '#swagger-ui' });
      </script>
    </body>
    </html>
    """;
  }

  /**
   * Adds one operation summary and its standard success response to the path map.
   *
   * @param paths mutable OpenAPI path map
   * @param path endpoint path
   * @param method lower-case HTTP method
   * @param summary operation summary
   */
  @SuppressWarnings("unchecked")
  private void add(Map<String, Object> paths, String path, String method, String summary) {
    Map<String, Object> operations =
        (Map<String, Object>) paths.computeIfAbsent(path, ignored -> new LinkedHashMap<>());
    operations.put(
        method,
        Map.of(
            "summary",
            summary,
            "responses",
            Map.of("200", Map.of("description", "Successful response"))));
  }
}
