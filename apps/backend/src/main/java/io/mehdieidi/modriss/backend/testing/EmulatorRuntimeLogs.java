package io.mehdieidi.modriss.backend.testing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Reads actual Floci Lambda stdout without exposing the Docker socket to project code. */
final class EmulatorRuntimeLogs {
  private final ObjectMapper mapper;

  EmulatorRuntimeLogs(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  List<ObjectNode> collect(ObjectNode request, String since) {
    Map<String, JsonNode> functions = new LinkedHashMap<>();
    request
        .path("resources")
        .forEach(
            resource -> {
              if (resource.path("ResourceType").asText().equals("AWS::Lambda::Function"))
                functions.put(resource.path("PhysicalResourceId").asText(), resource);
            });
    if (functions.isEmpty()) return List.of();
    List<ObjectNode> events = new ArrayList<>();
    String containers =
        command(
            List.of(
                "docker",
                "ps",
                "--filter",
                "label=io.floci.service=lambda",
                "--filter",
                "network=" + ProjectTestEnvironments.network(request.path("projectId").asText()),
                "--filter",
                "label=io.floci.account=" + request.path("accountId").asText(),
                "--filter",
                "label=io.floci.region=" + request.path("region").asText(),
                "--format",
                "{{.ID}}|{{.Label \"io.floci.resource-id\"}}"));
    int count = 0;
    for (String line : containers.split("\n")) {
      String[] fields = line.trim().split("\\|", 2);
      if (fields.length != 2
          || !fields[0].matches("[a-f0-9]{12,64}")
          || !functions.containsKey(fields[1])
          || count++ >= 20) continue;
      JsonNode function = functions.get(fields[1]);
      String output =
          command(
              List.of(
                  "docker", "logs", "--timestamps", "--since", since, "--tail", "200", fields[0]));
      for (String message : output.split("\n")) {
        int separator = message.indexOf(' ');
        if (separator < 0) continue;
        try {
          Instant timestamp = Instant.parse(message.substring(0, separator));
          ObjectNode event = mapper.createObjectNode();
          event.put("type", "runtime-log");
          event.put("at", timestamp.toEpochMilli() / 1000.0);
          event.put("timestamp", timestamp.toString());
          event.put("title", function.path("name").asText());
          event.put("resourceId", function.path("id").asText());
          event.put("source", "Floci Lambda container stdout/stderr");
          event.put("containerId", fields[0]);
          event.put("message", message.substring(separator + 1));
          events.add(event);
        } catch (java.time.format.DateTimeParseException ex) {
          /* Docker diagnostics are not application output. */
        }
      }
    }
    return events;
  }

  private String command(List<String> args) {
    Process process = null;
    try {
      process = new ProcessBuilder(args).redirectErrorStream(true).start();
      var input = process.getInputStream();
      CompletableFuture<byte[]> output =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  return input.readNBytes(262144);
                } catch (java.io.IOException ex) {
                  return new byte[0];
                }
              });
      if (!process.waitFor(6, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return "";
      }
      return process.exitValue() == 0
          ? new String(output.get(1, TimeUnit.SECONDS), StandardCharsets.UTF_8)
          : "";
    } catch (Exception ex) {
      return "";
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
    }
  }
}
