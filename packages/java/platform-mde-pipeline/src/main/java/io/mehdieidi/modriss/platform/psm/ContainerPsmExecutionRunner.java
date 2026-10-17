package io.mehdieidi.modriss.platform.psm;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.ObjectMapper;

/** Runs each upload in a fresh, unprivileged container with no mounts, network or credentials. */
public final class ContainerPsmExecutionRunner implements PsmExecutionRunner {
  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(ContainerPsmExecutionRunner.class);
  private final ObjectMapper mapper;
  private final String image;
  private final Duration timeout;

  public ContainerPsmExecutionRunner(ObjectMapper mapper, String image, Duration timeout) {
    this.mapper = mapper;
    this.image = image;
    this.timeout = timeout;
  }

  @Override
  public PsmWorkerResult run(PsmWorkerRequest request) {
    String name = "modriss-psm-" + UUID.randomUUID();
    Process process = null;
    var io = Executors.newFixedThreadPool(3);
    try {
      process =
          new ProcessBuilder(
                  List.of(
                      "docker",
                      "run",
                      "--rm",
                      "--pull=never",
                      "-i",
                      "--name",
                      name,
                      "--network",
                      "none",
                      "--read-only",
                      "--cap-drop=ALL",
                      "--security-opt=no-new-privileges",
                      "--pids-limit=128",
                      "--memory=768m",
                      "--cpus=1",
                      "--log-driver=none",
                      "--user=10001:10001",
                      "--tmpfs=/tmp:rw,noexec,nosuid,size=192m",
                      "--entrypoint=java",
                      image,
                      "-Xmx384m",
                      "-Dloader.main=io.mehdieidi.modriss.platform.psm.PsmPackageWorker",
                      "-cp",
                      "/app/backend.jar",
                      "org.springframework.boot.loader.launch.PropertiesLauncher"))
              .start();
      Process running = process;
      var stdout = io.submit(() -> bounded(running.getInputStream(), 100 * 1024 * 1024));
      var stderr = io.submit(() -> bounded(running.getErrorStream(), 128 * 1024));
      var stdin =
          io.submit(
              () -> {
                try (var output = running.getOutputStream()) {
                  mapper.writeValue(output, request);
                }
                return true;
              });
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new PlatformException(
            408, "Platform package execution timed out. Your saved models are unchanged.");
      }
      byte[] output = stdout.get(5, TimeUnit.SECONDS);
      byte[] error = stderr.get(5, TimeUnit.SECONDS);
      if (process.exitValue() != 0 || output.length == 0) {
        throw executionFailure(process.exitValue(), new String(error, StandardCharsets.UTF_8));
      }
      stdin.get(5, TimeUnit.SECONDS);
      return mapper.readValue(output, PsmWorkerResult.class);
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(
          503,
          "Platform package runner is unavailable. Check Docker access and the PSM runner image.");
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
      io.shutdownNow();
      try {
        Process cleanup =
            new ProcessBuilder("docker", "rm", "-f", name)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!cleanup.waitFor(5, TimeUnit.SECONDS)) cleanup.destroyForcibly();
      } catch (Exception ignored) {
        // The normal --rm path has already removed completed containers.
      }
    }
  }

  static PlatformException executionFailure(int code, String stderr) {
    String detail = stderr.toLowerCase(Locale.ROOT);
    String reason =
        detail.contains("permission denied")
            ? "Docker socket access denied"
            : detail.contains("cannot connect") || detail.contains("is the docker daemon running")
                ? "Docker daemon unavailable"
                : detail.contains("no such image") || detail.contains("unable to find image")
                    ? "Runner image missing"
                    : detail.contains("classnotfoundexception")
                            || detail.contains("could not find or load main class")
                        ? "Runner image has no PSM worker"
                        : code == 125 || code == 126 || code == 127
                            ? "Container could not start"
                            : null;
    if (reason != null || code == 0) {
      LOGGER.error(
          "PSM execution service unavailable: {} (container exit {}).",
          reason == null ? "Worker returned no result" : reason,
          code);
      return new PlatformException(
          503, "Platform checking is temporarily unavailable. Please try again shortly.");
    }
    return new PlatformException(
        422,
        "The platform scripts could not complete within the runner's resource limits. Check the"
            + " scripts and try again.");
  }

  private static byte[] bounded(InputStream stream, int maximum) throws Exception {
    byte[] result = stream.readNBytes(maximum + 1);
    if (result.length > maximum)
      throw new IllegalArgumentException("Runner output exceeds its size limit.");
    return result;
  }
}
