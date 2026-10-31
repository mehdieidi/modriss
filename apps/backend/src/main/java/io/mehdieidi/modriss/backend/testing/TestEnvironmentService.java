package io.mehdieidi.modriss.backend.testing;

import io.mehdieidi.modriss.platform.artifact.application.ArtifactService;
import io.mehdieidi.modriss.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Owns authenticated emulator jobs, immutable source snapshots, and durable execution evidence. */
@Service
public class TestEnvironmentService {
  private final ArtifactService artifacts;
  private final ProjectService projects;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final String image;
  private final ProjectTestEnvironments environments;
  private final String region;
  private final ThreadPoolExecutor workers =
      new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(12));
  private final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2);
  private final ScheduledExecutorService logTimers = Executors.newScheduledThreadPool(2);
  private final Map<String, Process> running = new java.util.concurrent.ConcurrentHashMap<>();

  public TestEnvironmentService(
      ArtifactService artifacts,
      ProjectService projects,
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      @Value("${modriss.testing.runner-image:modriss-test-runner:local}") String image,
      ProjectTestEnvironments environments,
      @Value("${modriss.testing.region:us-east-1}") String region) {
    this.artifacts = artifacts;
    this.projects = projects;
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.image = image;
    this.environments = environments;
    this.region = region;
  }

  @PostConstruct
  void recoverInterruptedJobs() {
    List<String> interrupted =
        jdbc.queryForList(
            "SELECT id FROM test_environment_jobs WHERE status IN ('QUEUED', 'RUNNING')",
            String.class);
    jdbc.update(
        "UPDATE test_environment_jobs SET status = 'INTERRUPTED', document = document ||"
            + " '{\"status\":\"INTERRUPTED\",\"error\":\"Backend restarted during this job. Inspect"
            + " Floci before retrying.\"}'::jsonb, updated_at = now() WHERE status IN ('QUEUED',"
            + " 'RUNNING')");
    interrupted.forEach(this::removeContainer);
  }

  @PreDestroy
  void close() {
    workers.shutdownNow();
    running.forEach(
        (id, process) -> {
          process.destroyForcibly();
          removeContainer(id);
        });
    timers.shutdownNow();
    logTimers.shutdownNow();
  }

  public ObjectNode overview(UserRecord user, String projectId, String before) {
    projects.get(user, projectId);
    ObjectNode result = mapper.createObjectNode();
    result.put("provider", "aws");
    result.put("emulator", "Floci");
    result.put("region", region);
    result.put("accountId", account(projectId));
    List<ObjectNode> jobs =
        before == null || before.isBlank()
            ? jdbc.query(
                "SELECT document::text FROM test_environment_jobs WHERE project_id = ? ORDER BY"
                    + " created_at DESC LIMIT 80",
                (rs, row) -> parse(rs.getString(1)),
                projectId)
            : jdbc.query(
                "SELECT document::text FROM test_environment_jobs WHERE project_id = ? AND"
                    + " created_at < (SELECT created_at FROM test_environment_jobs WHERE project_id"
                    + " = ? AND id = ?) ORDER BY created_at DESC LIMIT 80",
                (rs, row) -> parse(rs.getString(1)),
                projectId,
                projectId,
                before);
    result.set("jobs", mapper.valueToTree(jobs.stream().map(this::summary).toList()));
    if (jobs.size() == 80) result.put("historyCursor", jobs.get(79).path("id").asText());
    var deployments =
        jdbc.query(
            "SELECT document::text FROM test_environment_jobs WHERE project_id = ? AND kind ="
                + " 'DEPLOY' AND status = 'SUCCEEDED' ORDER BY created_at DESC LIMIT 1",
            (rs, row) -> parse(rs.getString(1)),
            projectId);
    result.set("deployment", deployments.isEmpty() ? mapper.nullNode() : deployments.get(0));
    ObjectNode environment = environments.inspect(projectId);
    result.set("environment", environment);
    var latestAttempts =
        jdbc.queryForList(
            "SELECT id FROM test_environment_jobs WHERE project_id = ? "
                + "AND kind = 'DEPLOY' ORDER BY created_at DESC LIMIT 1",
            String.class,
            projectId);
    result.put(
        "deploymentReady",
        !deployments.isEmpty()
            && !latestAttempts.isEmpty()
            && deployments.get(0).path("id").asText().equals(latestAttempts.get(0))
            && environments.matches(deployments.get(0), environment));
    result.set(
        "artifacts",
        mapper.valueToTree(
            artifacts.listSummaries(user, projectId).stream()
                .filter(this::isAws)
                .map(
                    a ->
                        Map.of(
                            "id",
                            a.id(),
                            "name",
                            a.name(),
                            "updatedAt",
                            a.updatedAt().toString(),
                            "templates",
                            templatePaths(a.files()),
                            "scripts",
                            scriptPaths(a.files())))
                .toList()));
    return result;
  }

  public ObjectNode get(UserRecord user, String projectId, String id) {
    projects.get(user, projectId);
    return load(projectId, id);
  }

  public ObjectNode submit(UserRecord user, String projectId, ObjectNode input) {
    var project = projects.get(user, projectId);
    projects.requireEditor(project, user.id());
    if (input.has("input") && !input.path("input").isObject()) {
      throw new PlatformException(400, "Execution input must be a JSON object.");
    }
    String kind = input.path("kind").asText("EXECUTE");
    if (!List.of("DEPLOY", "EXECUTE", "SCRIPT", "OBSERVE").contains(kind)) {
      throw new PlatformException(400, "Unknown test operation.");
    }
    ObjectNode request = input.deepCopy();
    request.put("kind", kind);
    ObjectNode job = mapper.createObjectNode();
    String id = UUID.randomUUID().toString();
    job.put("id", id);
    job.put("projectId", projectId);
    job.put("kind", kind);
    job.put("status", "QUEUED");
    job.put("createdAt", Instant.now().toString());
    job.put("provider", "aws");
    job.put("emulator", "Floci");
    job.put("runnerImage", image);
    job.put("region", region);
    job.put("accountId", account(projectId));
    job.put("resourceId", input.path("resourceId").asText(""));
    job.put("action", input.path("action").asText(""));
    job.put(
        "scenario",
        input
            .path("scenario")
            .asText("")
            .substring(0, Math.min(300, input.path("scenario").asText("").length())));
    job.set("input", input.path("input").deepCopy());
    job.set("events", mapper.createArrayNode());
    request.put("projectId", projectId);
    request.put("jobId", id);
    request.put("accountId", account(projectId));
    request.put("region", region);
    ObjectNode snapshot = null;
    String deploymentId = null;
    if (kind.equals("DEPLOY")) {
      ArtifactRecord artifact = artifacts.get(user, input.path("artifactId").asText(""));
      if (!artifact.projectId().equals(projectId) || !isAws(artifact)) {
        throw new PlatformException(
            400, "Only this project's built-in AWS artifacts can be deployed.");
      }
      if (templatePaths(artifact.files()).isEmpty()) {
        throw new PlatformException(400, "The artifact has no CloudFormation/SAM templates.");
      }
      if (artifact.files().values().stream().mapToLong(String::length).sum() > 30_000_000) {
        throw new PlatformException(413, "The artifact exceeds the 30 MB test snapshot limit.");
      }
      snapshot = mapper.createObjectNode();
      snapshot.set("files", mapper.valueToTree(artifact.files()));
      snapshot.put("artifactId", artifact.id());
      snapshot.put("artifactUpdatedAt", artifact.updatedAt().toString());
      snapshot.put("sourceHash", sourceHash(artifact.files()));
      request.set("files", snapshot.path("files"));
      job.put("artifactId", artifact.id());
      job.put("artifactName", artifact.name());
      job.put("artifactUpdatedAt", artifact.updatedAt().toString());
      job.put("sourceHash", snapshot.path("sourceHash").asText());
      job.set("scripts", mapper.valueToTree(scriptPaths(artifact.files())));
      deploymentId = id;
    } else {
      deploymentId = input.path("deploymentId").asText("");
      ObjectNode deployment = load(projectId, deploymentId);
      if (!deployment.path("kind").asText().equals("DEPLOY")
          || !deployment.path("status").asText().equals("SUCCEEDED")) {
        throw new PlatformException(409, "Deploy this project successfully before running tests.");
      }
      if (!environments.matches(deployment, environments.inspect(projectId))) {
        throw new PlatformException(
            409,
            "Redeploy this project to its dedicated Floci environment before testing. The"
                + " environment is missing, restarted, or belongs to the older shared emulator."
                + " Previous evidence is retained.");
      }
      request.set("environment", deployment.path("environment"));
      // Older source snapshots remain evidence, but cannot address resources replaced by
      // redeployment.
      String latest =
          jdbc.queryForObject(
              "SELECT id FROM test_environment_jobs WHERE project_id = ? AND kind = 'DEPLOY' "
                  + "ORDER BY created_at DESC LIMIT 1",
              String.class,
              projectId);
      if (!deploymentId.equals(latest)) {
        throw new PlatformException(
            409,
            "A newer deployment attempt changed this environment. Deploy successfully again before"
                + " testing.");
      }
      request.set("resources", deployment.path("result").path("resources"));
      job.put("artifactId", deployment.path("artifactId").asText());
      job.put("sourceHash", deployment.path("sourceHash").asText());
      if (kind.equals("EXECUTE") || (kind.equals("OBSERVE") && input.has("resourceId"))) {
        JsonNode resource =
            java.util.stream.StreamSupport.stream(
                    deployment.path("result").path("resources").spliterator(), false)
                .filter(
                    candidate ->
                        candidate.path("id").asText().equals(input.path("resourceId").asText()))
                .findFirst()
                .orElseThrow(
                    () ->
                        new PlatformException(
                            400, "Resource is not part of the deployed project."));
        if (kind.equals("EXECUTE")
            && !java.util.stream.StreamSupport.stream(resource.path("actions").spliterator(), false)
                .anyMatch(value -> value.asText().equals(input.path("action").asText()))) {
          throw new PlatformException(
              400, "This action is not supported by the deployed resource.");
        }
      }
      if (kind.equals("SCRIPT")) {
        ObjectNode saved = snapshot(projectId, deploymentId);
        String script = input.path("resourceId").asText();
        if (!deployment.path("scripts").isArray()
            || !java.util.stream.StreamSupport.stream(
                    deployment.path("scripts").spliterator(), false)
                .anyMatch(node -> node.asText().equals(script))) {
          throw new PlatformException(400, "Choose a script from the deployed snapshot.");
        }
        request.set("files", saved.path("files"));
      }
    }
    job.put("deploymentId", deploymentId);
    try {
      jdbc.update(
          "INSERT INTO test_environment_jobs(id, project_id, kind, status, deployment_id, document,"
              + " snapshot) VALUES (?, ?, ?, 'QUEUED', ?, ?::jsonb, ?::jsonb)",
          id,
          projectId,
          kind,
          deploymentId,
          mapper.writeValueAsString(job),
          snapshot == null ? null : mapper.writeValueAsString(snapshot));
    } catch (DuplicateKeyException ex) {
      throw new PlatformException(
          409, "Another operation is running in this project's test environment.");
    }
    try {
      workers.execute(() -> run(job.deepCopy(), request));
    } catch (RejectedExecutionException ex) {
      job.put("status", "FAILED");
      job.put("error", "Test runner queue is full. Retry when another operation finishes.");
      persist(job);
      throw new PlatformException(503, "Test runner queue is full.");
    }
    return job;
  }

  private void run(ObjectNode job, ObjectNode request) {
    String id = job.path("id").asText();
    Process process = null;
    CompletableFuture<Void> inputWriter = null;
    java.util.concurrent.ScheduledFuture<?> timeout = null;
    java.util.concurrent.ScheduledFuture<?> logCapture = null;
    var observedLogs = new java.util.concurrent.ConcurrentLinkedQueue<ObjectNode>();
    var seenLogs = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
    var runtimeBytes = new java.util.concurrent.atomic.AtomicLong();
    Object logLock = new Object();
    String started = Instant.now().toString();
    Runnable capture =
        () -> {
          synchronized (logLock) {
            for (ObjectNode event :
                new EmulatorRuntimeLogs(mapper)
                    .collect(
                        request,
                        request.path("action").asText().equals("logs")
                            ? Instant.EPOCH.toString()
                            : started)) {
              String identity =
                  event.path("containerId").asText()
                      + event.path("timestamp").asText()
                      + event.path("message").asText();
              if (runtimeBytes.get() <= 2_000_000
                  && seenLogs.size() < 2000
                  && seenLogs.add(identity)
                  && runtimeBytes.addAndGet(event.path("message").asText().length() + 500)
                      <= 2_000_000) observedLogs.add(event);
            }
          }
        };
    boolean completed = false;
    String completionStatus = "FAILED";
    StringBuilder runnerOutput = new StringBuilder();
    try {
      job.put("status", "RUNNING");
      job.put("startedAt", Instant.now().toString());
      persist(job);
      String projectId = job.path("projectId").asText();
      recordStage(job, "Checking the Floci test runner image");
      String runnerSetup =
          image.equals("modriss-test-runner:local")
              ? "Run `docker compose --profile test-tools build test-runner`, then retry."
              : "Build or pull this image into Docker, then retry.";
      environments.requireImage(image, "The Floci test runner", runnerSetup);
      recordStage(job, "Checking Docker and preparing this project's Floci instance");
      ObjectNode environment =
          request.path("kind").asText().equals("DEPLOY")
              ? environments.ensure(projectId, title -> recordStage(job, title))
              : environments.inspect(projectId);
      if (!request.path("kind").asText().equals("DEPLOY")
          && !environments.matches(request, environment))
        throw new IllegalStateException(
            "The project's Floci instance changed after this job was queued. Redeploy before"
                + " testing.");
      request.set("environment", environment);
      job.set("environment", environment);
      job.withArray("events")
          .add(
              mapper
                  .createObjectNode()
                  .put("type", "stage")
                  .put("at", System.currentTimeMillis() / 1000.0)
                  .put("title", "Using this project's dedicated Floci instance")
                  .put("container", environment.path("container").asText()));
      persist(job);
      if (!request.path("kind").asText().equals("DEPLOY"))
        logCapture = logTimers.scheduleWithFixedDelay(capture, 2, 4, TimeUnit.SECONDS);
      List<String> args =
          new ArrayList<>(
              List.of(
                  "docker",
                  "run",
                  "--rm",
                  "-i",
                  "--name",
                  "modriss-test-" + id,
                  "--network",
                  environment.path("network").asText(),
                  "--read-only",
                  "--cap-drop=ALL",
                  "--security-opt=no-new-privileges",
                  "--cpus=2",
                  "--memory=2g",
                  "--pids-limit=256",
                  "--tmpfs",
                  "/tmp:rw,exec,nosuid,size=2147483648,mode=1777",
                  "-e",
                  "HOME=/tmp/home",
                  "-e",
                  "GOCACHE=/tmp/go-cache",
                  "-e",
                  "GOPATH=/tmp/go",
                  "-e",
                  "MODRISS_TEST_ENDPOINT=http://aws-emulator:4566",
                  image));
      process = new ProcessBuilder(args).redirectErrorStream(true).start();
      running.put(id, process);
      Process active = process;
      timeout =
          timers.schedule(
              () -> {
                removeContainer(id);
                active.destroyForcibly();
              },
              900,
              TimeUnit.SECONDS);
      byte[] requestBytes = mapper.writeValueAsBytes(request);
      Process runner = process;
      inputWriter =
          CompletableFuture.runAsync(
              () -> {
                try (var output = runner.getOutputStream()) {
                  output.write(requestBytes);
                } catch (java.io.IOException ex) {
                  throw new CompletionException(ex);
                }
              });
      long lastSave = 0;
      int captured = 0;
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = boundedLine(reader)) != null) {
          drainRuntimeLogs(job, observedLogs);
          captured += line.length();
          ObjectNode event;
          try {
            event = parse(line);
          } catch (Exception ex) {
            event = mapper.createObjectNode().put("type", "log").put("message", line);
          }
          String type = event.path("type").asText();
          if (type.equals("log") && runnerOutput.length() < 4_000) {
            runnerOutput.append(event.path("message").asText()).append('\n');
          }
          if (type.equals("result")) job.set("result", event.path("result"));
          else if (type.equals("error")) job.put("error", event.path("message").asText());
          else if (type.equals("complete")) {
            completed = true;
            completionStatus = event.path("status").asText("FAILED");
          } else if (captured <= 8_000_000 && job.withArray("events").size() < 5000)
            job.withArray("events").add(event);
          else job.put("eventsTruncated", true);
          if (System.currentTimeMillis() - lastSave > 700 || type.equals("complete")) {
            persist(job);
            lastSave = System.currentTimeMillis();
          }
        }
      }
      int exit = process.waitFor();
      String inputError = null;
      if (inputWriter != null) {
        try {
          inputWriter.join();
        } catch (CompletionException ex) {
          Throwable cause = ex.getCause();
          inputError = cause == null ? ex.getMessage() : cause.getMessage();
        }
      }
      job.put("status", completionStatus);
      if (exit != 0 || !completed || inputError != null) {
        job.put("status", "FAILED");
        if (!job.has("error")) {
          StringBuilder error =
              new StringBuilder("Test runner stopped before completion (exit ")
                  .append(exit)
                  .append(").");
          if (inputError != null)
            error.append(" Request delivery failed: ").append(inputError).append('.');
          if (!runnerOutput.isEmpty())
            error.append(" Docker output: ").append(runnerOutput.toString().strip());
          error.append(
              " Check the runner image, Docker network, Floci health, and the 15 minute execution"
                  + " limit.");
          job.put("error", error.toString());
        }
      }
      if (captured > 8_000_000) job.put("eventsTruncated", true);
    } catch (Exception ex) {
      job.put("status", "FAILED");
      job.put("error", "Could not execute Floci job: " + ex.getMessage());
      if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
    } finally {
      if (logCapture != null) {
        logCapture.cancel(false);
        capture.run();
        drainRuntimeLogs(job, observedLogs);
        if (runtimeBytes.get() > 2_000_000 || seenLogs.size() >= 2000)
          job.put("eventsTruncated", true);
        if (job.path("action").asText().equals("logs") && job.path("result").isObject()) {
          var runtime = mapper.createArrayNode();
          for (JsonNode event : job.path("events")) {
            if (event.path("type").asText().equals("runtime-log")
                && event.path("resourceId").asText().equals(job.path("resourceId").asText()))
              runtime.add(event);
          }
          ((ObjectNode) job.path("result")).set("runtimeEvents", runtime);
        }
      }
      if (timeout != null) timeout.cancel(false);
      if (process != null && process.isAlive()) process.destroyForcibly();
      running.remove(id);
      removeContainer(id);
      job.put("finishedAt", Instant.now().toString());
      persist(job);
    }
  }

  private void drainRuntimeLogs(ObjectNode job, java.util.Queue<ObjectNode> logs) {
    ObjectNode event;
    while ((event = logs.poll()) != null) {
      if (job.withArray("events").size() < 5000) job.withArray("events").add(event);
      else job.put("eventsTruncated", true);
      if (job.path("action").asText().equals("logs")
          && job.path("result").isObject()
          && event.path("resourceId").asText().equals(job.path("resourceId").asText())) {
        ((ObjectNode) job.path("result")).withArray("runtimeEvents").add(event);
      }
    }
  }

  private void recordStage(ObjectNode job, String title) {
    job.withArray("events")
        .add(
            mapper
                .createObjectNode()
                .put("type", "stage")
                .put("at", System.currentTimeMillis() / 1000.0)
                .put("title", title));
    persist(job);
  }

  private void removeContainer(String id) {
    try {
      Process cleanup =
          new ProcessBuilder("docker", "rm", "-f", "modriss-test-" + id)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      if (!cleanup.waitFor(10, TimeUnit.SECONDS)) cleanup.destroyForcibly();
    } catch (Exception ex) {
      /* Docker may already have removed the disposable container. */
    }
  }

  private String boundedLine(BufferedReader reader) throws java.io.IOException {
    StringBuilder line = new StringBuilder();
    int value;
    boolean truncated = false;
    while ((value = reader.read()) != -1 && value != '\n') {
      if (line.length() < 3_000_000) line.append((char) value);
      else truncated = true;
    }
    if (value == -1 && line.isEmpty()) return null;
    return truncated
        ? mapper.writeValueAsString(
            Map.of(
                "type",
                "log",
                "message",
                "A runner observation exceeded the 3 MB line limit and was omitted."))
        : line.toString();
  }

  private ObjectNode load(String projectId, String id) {
    return jdbc
        .query(
            "SELECT document::text FROM test_environment_jobs WHERE project_id = ? AND id = ?",
            (rs, row) -> parse(rs.getString(1)),
            projectId,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new PlatformException(404, "Test job not found in this project."));
  }

  private ObjectNode snapshot(String projectId, String id) {
    return jdbc
        .query(
            "SELECT snapshot::text FROM test_environment_jobs WHERE project_id = ? AND id = ?",
            (rs, row) -> parse(rs.getString(1)),
            projectId,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new PlatformException(404, "Deployment snapshot not found."));
  }

  private void persist(ObjectNode job) {
    jdbc.update(
        "UPDATE test_environment_jobs SET status = ?, document = ?::jsonb, updated_at = now() WHERE"
            + " id = ?",
        job.path("status").asText(),
        mapper.writeValueAsString(job),
        job.path("id").asText());
  }

  private ObjectNode summary(ObjectNode job) {
    ObjectNode copy = job.deepCopy();
    copy.remove(List.of("events", "result", "input"));
    return copy;
  }

  private ObjectNode parse(String json) {
    return (ObjectNode) mapper.readTree(json);
  }

  private boolean isAws(ArtifactRecord artifact) {
    return artifact.modelJson() == null
        || artifact.modelJson().path("psmPackageId").asText("aws").equals("aws");
  }

  private List<String> templatePaths(Map<String, String> files) {
    // Summaries contain paths only. Template content is checked again by the worker.
    return files.keySet().stream()
        .filter(
            path ->
                path.matches("(?i).*(template|stack)[^/]*\\.(yaml|yml|json)$")
                    || ((path.endsWith(".yaml") || path.endsWith(".yml") || path.endsWith(".json"))
                        && (files.get(path).contains("AWSTemplateFormatVersion")
                            || files.get(path).contains("AWS::Serverless-2016-10-31")
                            || java.util.regex.Pattern.compile(
                                    "(?m)^Resources\\s*:|\"Resources\"\\s*:")
                                .matcher(files.get(path))
                                .find())))
        .sorted()
        .toList();
  }

  private List<String> scriptPaths(Map<String, String> files) {
    return files.keySet().stream()
        .filter(path -> path.startsWith("scripts/") || path.startsWith("tests/"))
        .filter(path -> path.endsWith(".sh") || path.endsWith(".py"))
        .sorted()
        .toList();
  }

  static String account(String projectId) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(projectId.getBytes(StandardCharsets.UTF_8));
      return String.format(
          "%012d",
          new java.math.BigInteger(1, hash)
              .mod(new java.math.BigInteger("900000000000"))
              .add(new java.math.BigInteger("100000000000"))
              .longValue());
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  static String sourceHash(Map<String, String> files) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      files.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              entry -> {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(entry.getValue().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
              });
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
