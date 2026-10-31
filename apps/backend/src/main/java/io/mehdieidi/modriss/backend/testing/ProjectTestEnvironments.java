package io.mehdieidi.modriss.backend.testing;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Keeps application authentication separate from AWS management credentials. */
@Service
public class ProjectTestEnvironments {
  static final String OWNER = "io.modriss.test-environment";
  static final String PROJECT = "io.modriss.project-id";
  static final String MODE = "dedicated-floci-v1";
  private static final Logger log = LoggerFactory.getLogger(ProjectTestEnvironments.class);
  private static final int MAX_DOCKER_OUTPUT_BYTES = 1_000_000;
  private static final int IMAGE_PULL_TIMEOUT_SECONDS = 600;
  private final ObjectMapper mapper;
  private final JdbcTemplate jdbc;
  private final String image;
  private final String region;
  private final Docker docker;
  private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();

  @org.springframework.beans.factory.annotation.Autowired
  public ProjectTestEnvironments(
      ObjectMapper mapper,
      JdbcTemplate jdbc,
      @Value("${modriss.testing.emulator-image:floci/floci:2.2.0}") String image,
      @Value("${modriss.testing.region:us-east-1}") String region) {
    this(mapper, jdbc, image, region, ProjectTestEnvironments::command);
  }

  ProjectTestEnvironments(
      ObjectMapper mapper, JdbcTemplate jdbc, String image, String region, Docker docker) {
    this.mapper = mapper;
    this.jdbc = jdbc;
    this.image = image;
    this.region = region;
    this.docker = docker;
  }

  @PostConstruct
  void startCleanup() {
    cleanup.scheduleWithFixedDelay(this::cleanDeletedProjects, 60, 60, TimeUnit.SECONDS);
  }

  @PreDestroy
  void close() {
    cleanup.shutdownNow();
  }

  static String suffix(String projectId) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(projectId.getBytes(StandardCharsets.UTF_8)))
          .substring(0, 24);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  static String container(String projectId) {
    return "modriss-floci-" + suffix(projectId);
  }

  static String network(String projectId) {
    return "modriss-testnet-" + suffix(projectId);
  }

  static String volume(String projectId) {
    return "modriss-testdata-" + suffix(projectId);
  }

  ObjectNode inspect(String projectId) {
    ObjectNode environment =
        mapper
            .createObjectNode()
            .put("mode", MODE)
            .put("container", container(projectId))
            .put("network", network(projectId))
            .put("status", "MISSING");
    Result result = docker.run(List.of("inspect", container(projectId)), 10);
    if (result.exit() != 0) return environment;
    JsonNode value = mapper.readTree(result.output()).path(0);
    requireOwner(value.path("Config").path("Labels"), projectId);
    if (!value.path("NetworkSettings").path("Networks").has(network(projectId)))
      throw new IllegalStateException(
          "The project's Floci instance is not on its private test network.");
    environment.put(
        "id", value.path("Id").asText() + ":" + value.path("State").path("StartedAt").asText());
    environment.put("image", value.path("Config").path("Image").asText());
    environment.put(
        "status", value.path("State").path("Running").asBoolean() ? "RUNNING" : "STOPPED");
    return environment;
  }

  ObjectNode ensure(String projectId) {
    return ensure(projectId, ignored -> {});
  }

  synchronized ObjectNode ensure(String projectId, Consumer<String> progress) {
    ObjectNode current = inspect(projectId);
    if (!current.path("status").asText().equals("MISSING")
        && !current.path("image").asText().isEmpty()
        && !image.equals(current.path("image").asText())) {
      // Flush persisted state, retain an independent copy, then reuse the original data volume.
      // Never reset a project's data to upgrade the emulator.
      ensureImageAvailable(progress);
      checked(List.of("stop", "--time", "30", container(projectId)), 45);
      String backup = volume(projectId) + "-backup-" + System.currentTimeMillis();
      try {
        ownedResource("volume", backup, projectId);
        checked(
            List.of(
                "run",
                "--rm",
                "--network",
                "none",
                "--entrypoint",
                "cp",
                "-v",
                volume(projectId) + ":/source:ro",
                "-v",
                backup + ":/backup",
                image,
                "-a",
                "/source/.",
                "/backup/"),
            120);
      } catch (RuntimeException ex) {
        checked(List.of("start", container(projectId)), 30);
        throw ex;
      }
      checked(List.of("rm", container(projectId)), 30);
      log.info(
          "Upgrading project {} emulator from {} to {}; retained data backup {}",
          projectId,
          current.path("image").asText(),
          image,
          backup);
      current = inspect(projectId);
    }
    if (current.path("status").asText().equals("MISSING")) {
      ensureImageAvailable(progress);
      progress.accept("Preparing this project's private Docker network and storage");
      ownedResource("network", network(projectId), projectId);
      ownedResource("volume", volume(projectId), projectId);
      progress.accept("Starting this project's Floci instance");
      List<String> args =
          new ArrayList<>(
              List.of(
                  "run",
                  "--pull=never",
                  "-d",
                  "--name",
                  container(projectId),
                  "--label",
                  OWNER + "=true",
                  "--label",
                  PROJECT + "=" + projectId,
                  "--network",
                  network(projectId),
                  "--network-alias",
                  "aws-emulator",
                  "--restart",
                  "unless-stopped",
                  "--cpus=2",
                  "--memory=2g",
                  "--pids-limit=1024",
                  "--log-opt",
                  "max-size=10m",
                  "--log-opt",
                  "max-file=3",
                  "-v",
                  "/var/run/docker.sock:/var/run/docker.sock",
                  "-v",
                  volume(projectId) + ":/app/data",
                  "-e",
                  "FLOCI_DEFAULT_ACCOUNT_ID=" + TestEnvironmentService.account(projectId),
                  "-e",
                  "FLOCI_DEFAULT_REGION=" + region,
                  "-e",
                  "FLOCI_HOSTNAME=aws-emulator",
                  "-e",
                  "FLOCI_STORAGE_MODE=hybrid",
                  "-e",
                  "FLOCI_STORAGE_PERSISTENT_PATH=/app/data",
                  "-e",
                  "FLOCI_DOCKER_RESOURCE_NAMESPACE=" + container(projectId),
                  "-e",
                  "FLOCI_SERVICES_CLOUDFORMATION_ALLOW_STUB_UNSUPPORTED_RESOURCE_TYPES=false",
                  image));
      checked(args, 60);
    } else if (current.path("status").asText().equals("STOPPED")) {
      progress.accept("Restarting this project's Floci instance");
      checked(List.of("start", container(projectId)), 30);
    }
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
    while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
      // Use Floci's image-owned probe: 2.2+ no longer includes curl. The worker
      // independently verifies the HTTP health payload and account before any action.
      Result health =
          docker.run(List.of("exec", container(projectId), "/usr/local/bin/healthcheck.sh"), 6);
      if (health.exit() == 0) return inspect(projectId);
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    throw new IllegalStateException(
        "The project's Floci instance did not become healthy within 90 seconds.");
  }

  private void ensureImageAvailable(Consumer<String> progress) {
    Result imageStatus = inspectImage(image, "Floci");
    if (imageStatus == null) return;
    progress.accept("Downloading the Floci image " + image);
    Result pulled = docker.run(List.of("pull", "-q", image), IMAGE_PULL_TIMEOUT_SECONDS);
    if (pulled.exit() != 0) {
      throw new IllegalStateException(
          "Could not download the configured Floci image '"
              + image
              + "': "
              + dockerMessage(pulled));
    }
    progress.accept("Floci image download complete");
  }

  void requireImage(String candidate, String purpose, String remediation) {
    Result missing = inspectImage(candidate, purpose);
    if (missing == null) return;
    String message = missing.output().toLowerCase(Locale.ROOT);
    if (message.contains("no such image") || message.contains("no such resource")) {
      throw new IllegalStateException(
          purpose + " image '" + candidate + "' is missing. " + remediation);
    }
    throw new IllegalStateException(
        "Could not inspect "
            + purpose.toLowerCase(Locale.ROOT)
            + " image '"
            + candidate
            + "': "
            + dockerMessage(missing));
  }

  /** Returns null when the image is available and the Docker result when it is not. */
  private Result inspectImage(String candidate, String purpose) {
    Result status = docker.run(List.of("image", "inspect", candidate), 15);
    if (status.exit() == 0) return null;
    String message = status.output().toLowerCase(Locale.ROOT);
    if (message.contains("no such image") || message.contains("no such resource")) return status;
    throw new IllegalStateException(
        "Could not inspect the configured "
            + purpose
            + " image '"
            + candidate
            + "': "
            + dockerMessage(status));
  }

  private String dockerMessage(Result result) {
    String output = result.output().strip();
    if (output.length() > 2_000) output = output.substring(0, 2_000) + "…";
    return output.isEmpty() ? "Docker exited with status " + result.exit() + "." : output;
  }

  boolean matches(JsonNode deployment, JsonNode environment) {
    return MODE.equals(deployment.path("environment").path("mode").asText())
        && environment.path("status").asText().equals("RUNNING")
        && !environment.path("id").asText().isEmpty()
        && environment
            .path("id")
            .asText()
            .equals(deployment.path("environment").path("id").asText());
  }

  private void ownedResource(String type, String name, String projectId) {
    Result result = docker.run(List.of(type, "inspect", name), 10);
    if (result.exit() == 0) {
      requireOwner(mapper.readTree(result.output()).path(0).path("Labels"), projectId);
    } else {
      checked(
          List.of(
              type,
              "create",
              "--label",
              OWNER + "=true",
              "--label",
              PROJECT + "=" + projectId,
              name),
          15);
    }
  }

  private void requireOwner(JsonNode labels, String projectId) {
    if (!labels.path(OWNER).asText().equals("true")
        || !labels.path(PROJECT).asText().equals(projectId))
      throw new IllegalStateException(
          "Refusing to use a Docker resource not owned by this project's test environment.");
  }

  /** Reconciles all managed resource types, including leftovers after a partially failed create. */
  synchronized void cleanDeletedProjects() {
    try {
      var cleanedChildren = new java.util.HashSet<String>();
      for (String type : List.of("container", "volume", "network")) {
        Result list =
            docker.run(
                type.equals("container")
                    ? List.of(
                        "ps",
                        "-a",
                        "--filter",
                        "label=" + OWNER + "=true",
                        "--format",
                        "{{.Names}}")
                    : List.of(
                        type,
                        "ls",
                        "--filter",
                        "label=" + OWNER + "=true",
                        "--format",
                        "{{.Name}}"),
                10);
        if (list.exit() != 0) continue;
        for (String name : list.output().lines().filter(line -> !line.isBlank()).toList()) {
          Result details = docker.run(List.of(type, "inspect", name), 10);
          if (details.exit() != 0) continue;
          JsonNode item = mapper.readTree(details.output()).path(0);
          JsonNode labels =
              type.equals("container") ? item.path("Config").path("Labels") : item.path("Labels");
          String projectId = labels.path(PROJECT).asText();
          requireOwner(labels, projectId);
          String expected =
              switch (type) {
                case "container" -> container(projectId);
                case "volume" -> volume(projectId);
                default -> network(projectId);
              };
          boolean managedName =
              name.equals(expected)
                  || (type.equals("volume") && name.startsWith(expected + "-backup-"));
          if (!managedName
              || Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "SELECT EXISTS(SELECT 1 FROM projects WHERE id = ?)",
                      Boolean.class,
                      projectId))) continue;
          if (type.equals("container")) {
            checked(List.of("rm", "-f", name), 30);
          }
          if (cleanedChildren.add(projectId)) removeChildren(projectId);
          if (!type.equals("container")) checked(List.of(type, "rm", name), 20);
        }
      }
    } catch (Exception ex) {
      log.warn("Could not clean deleted project test environments: {}", ex.getMessage());
    }
  }

  private void removeChildren(String projectId) {
    // Also handles partial cleanup where the emulator container is already gone.
    Result containers =
        docker.run(
            List.of(
                "ps",
                "-a",
                "--filter",
                "label=floci_namespace=" + container(projectId),
                "--format",
                "{{.ID}}"),
            10);
    if (containers.exit() != 0)
      throw new IllegalStateException("Could not list project service containers.");
    for (String child :
        containers.output().lines().filter(id -> id.matches("[a-f0-9]{12,64}")).toList())
      checked(List.of("rm", "-f", "-v", child), 20);
    Result volumes =
        docker.run(
            List.of(
                "volume",
                "ls",
                "--filter",
                "label=floci_namespace=" + container(projectId),
                "--format",
                "{{.Name}}"),
            10);
    if (volumes.exit() != 0)
      throw new IllegalStateException("Could not list project service volumes.");
    for (String child :
        volumes
            .output()
            .lines()
            .filter(value -> value.matches("[A-Za-z0-9][A-Za-z0-9_.-]+"))
            .toList()) checked(List.of("volume", "rm", child), 20);
    Result networks =
        docker.run(
            List.of(
                "network",
                "ls",
                "--filter",
                "label=floci_namespace=" + container(projectId),
                "--format",
                "{{.Name}}"),
            10);
    if (networks.exit() != 0)
      throw new IllegalStateException("Could not list project service networks.");
    for (String child :
        networks
            .output()
            .lines()
            .filter(value -> value.matches("[A-Za-z0-9][A-Za-z0-9_.-]+"))
            .toList()) checked(List.of("network", "rm", child), 20);
  }

  private String checked(List<String> args, int seconds) {
    Result result = docker.run(args, seconds);
    if (result.exit() != 0)
      throw new IllegalStateException(
          "Docker test environment operation failed: " + result.output());
    return result.output();
  }

  record Result(int exit, String output) {}

  @FunctionalInterface
  interface Docker {
    Result run(List<String> args, int seconds);
  }

  private static Result command(List<String> args, int seconds) {
    Process process = null;
    try {
      List<String> command = new ArrayList<>(List.of("docker"));
      command.addAll(args);
      process = new ProcessBuilder(command).redirectErrorStream(true).start();
      var input = process.getInputStream();
      var output =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  ByteArrayOutputStream captured = new ByteArrayOutputStream();
                  byte[] buffer = new byte[8192];
                  int count;
                  while ((count = input.read(buffer)) != -1) {
                    int remaining = MAX_DOCKER_OUTPUT_BYTES - captured.size();
                    if (remaining > 0) captured.write(buffer, 0, Math.min(count, remaining));
                  }
                  return captured.toByteArray();
                } catch (java.io.IOException ex) {
                  throw new IllegalStateException(ex);
                }
              });
      if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
        throw new IllegalStateException(
            "Docker '"
                + args.get(0)
                + "' command timed out after "
                + seconds
                + " seconds. Check that the Docker daemon is responsive and retry.");
      }
      return new Result(
          process.exitValue(),
          new String(output.get(10, TimeUnit.SECONDS), StandardCharsets.UTF_8));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for Docker.", ex);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException(
          "Could not start the Docker CLI. Verify that Docker is installed and available to the"
              + " backend: "
              + ex.getMessage(),
          ex);
    } catch (ExecutionException | TimeoutException ex) {
      throw new IllegalStateException(
          "Could not read Docker command output: " + ex.getMessage(), ex);
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
    }
  }
}
