package io.mehdieidi.modriss.platform.psm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ContainerPsmExecutionRunnerTest {
  @Test
  void dockerAccessAndImageFailuresAreServiceFailuresRatherThanInvalidPackages() {
    assertEquals(
        503,
        ContainerPsmExecutionRunner.executionFailure(
                1,
                "permission denied while trying to connect to the docker API at"
                    + " unix:///var/run/docker.sock")
            .status());
    assertEquals(
        503,
        ContainerPsmExecutionRunner.executionFailure(125, "Cannot connect to the Docker daemon")
            .status());
    assertEquals(
        503,
        ContainerPsmExecutionRunner.executionFailure(125, "No such image: modriss-backend:local")
            .status());
    assertEquals(
        503,
        ContainerPsmExecutionRunner.executionFailure(1, "ClassNotFoundException: PsmPackageWorker")
            .status());
    assertEquals(422, ContainerPsmExecutionRunner.executionFailure(137, "").status());
  }
}
