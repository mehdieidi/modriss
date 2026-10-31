package io.mehdieidi.modriss.backend.testing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ProjectTestEnvironmentsTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

  private String containerJson(String project, boolean running, String start) {
    return mapper.writeValueAsString(
        List.of(
            Map.of(
                "Id",
                "instance-" + project,
                "State",
                Map.of("Running", running, "StartedAt", start),
                "Config",
                Map.of(
                    "Image",
                    "floci:test",
                    "Labels",
                    Map.of(
                        ProjectTestEnvironments.OWNER,
                        "true",
                        ProjectTestEnvironments.PROJECT,
                        project)),
                "NetworkSettings",
                Map.of("Networks", Map.of(ProjectTestEnvironments.network(project), Map.of())))));
  }

  private ProjectTestEnvironments.Result ok(String output) {
    return new ProjectTestEnvironments.Result(0, output);
  }

  private ProjectTestEnvironments.Result missing() {
    return new ProjectTestEnvironments.Result(1, "No such resource");
  }

  @Test
  void eachProjectGetsItsOwnDefaultAccountNetworkAndVolume() {
    var commands = new ArrayList<List<String>>();
    var created = new java.util.HashSet<String>();
    var environments =
        new ProjectTestEnvironments(
            mapper,
            jdbc,
            "floci:test",
            "us-east-1",
            (args, seconds) -> {
              commands.add(args);
              if (args.get(0).equals("inspect")) {
                for (String project : Set.of("first", "second"))
                  if (args.get(1).equals(ProjectTestEnvironments.container(project))
                      && created.contains(project))
                    return ok(containerJson(project, true, "start"));
                return missing();
              }
              if (args.contains("inspect")) return missing();
              if (args.get(0).equals("run")) {
                for (String project : Set.of("first", "second"))
                  if (args.contains(ProjectTestEnvironments.container(project)))
                    created.add(project);
              }
              return ok(
                  args.get(0).equals("exec")
                      ? "{\"version\":\"test\",\"services\":{}}"
                      : "created");
            });
    try {
      ObjectNode first = environments.ensure("first"), second = environments.ensure("second");
      assertThat(first.path("network").asText()).isNotEqualTo(second.path("network").asText());
      for (String project : Set.of("first", "second")) {
        List<String> run =
            commands.stream()
                .filter(
                    args ->
                        args.get(0).equals("run")
                            && args.contains(ProjectTestEnvironments.container(project)))
                .findFirst()
                .orElseThrow();
        assertThat(run)
            .contains(
                "FLOCI_DEFAULT_ACCOUNT_ID=" + TestEnvironmentService.account(project),
                ProjectTestEnvironments.network(project),
                ProjectTestEnvironments.volume(project) + ":/app/data",
                "--network-alias",
                "aws-emulator",
                "FLOCI_STORAGE_MODE=hybrid");
        assertThat(run).doesNotContain("-p", "--publish", "modriss_default");
      }
    } finally {
      environments.close();
    }
  }

  @Test
  void reusesRunningInstanceAndRestartsStoppedInstanceWithoutReplacingVolume() {
    var commands = new ArrayList<List<String>>();
    var running = new java.util.concurrent.atomic.AtomicBoolean(false);
    var environments =
        new ProjectTestEnvironments(
            mapper,
            jdbc,
            "floci:test",
            "us-east-1",
            (args, seconds) -> {
              commands.add(args);
              if (args.get(0).equals("inspect"))
                return ok(containerJson("project", running.get(), "start"));
              if (args.get(0).equals("start")) running.set(true);
              return ok("{\"version\":\"test\",\"services\":{}}");
            });
    try {
      environments.ensure("project");
      environments.ensure("project");
      assertThat(commands.stream().filter(args -> args.get(0).equals("start")).count())
          .isEqualTo(1);
      assertThat(
              commands.stream()
                  .anyMatch(args -> args.contains("create") || args.get(0).equals("run")))
          .isFalse();
    } finally {
      environments.close();
    }
  }

  @Test
  void emulatorUpgradeBacksUpAndReusesTheProjectVolume() {
    var commands = new ArrayList<List<String>>();
    var exists = new java.util.concurrent.atomic.AtomicBoolean(true);
    var upgraded = new java.util.concurrent.atomic.AtomicBoolean(false);
    var environments =
        new ProjectTestEnvironments(
            mapper,
            jdbc,
            "floci:new",
            "us-east-1",
            (args, seconds) -> {
              commands.add(args);
              if (args.get(0).equals("inspect"))
                return exists.get()
                    ? ok(
                        containerJson("project", true, "start")
                            .replace("floci:test", upgraded.get() ? "floci:new" : "floci:old"))
                    : missing();
              if (args.contains("inspect") && !args.get(0).equals("image")) return missing();
              if (args.get(0).equals("rm")) exists.set(false);
              if (args.get(0).equals("run") && args.contains("--name")) {
                exists.set(true);
                upgraded.set(true);
              }
              return ok(
                  args.get(0).equals("exec")
                      ? "{\"version\":\"2.2.0\",\"services\":{}}"
                      : "created");
            });
    try {
      assertThat(environments.ensure("project").path("image").asText()).isEqualTo("floci:new");
      var copy =
          commands.stream().filter(args -> args.contains("/source/.")).findFirst().orElseThrow();
      assertThat(copy)
          .contains(ProjectTestEnvironments.volume("project") + ":/source:ro", "--network", "none");
      assertThat(commands.indexOf(copy))
          .isLessThan(
              commands.indexOf(List.of("rm", ProjectTestEnvironments.container("project"))));
      assertThat(
              commands.stream()
                  .anyMatch(
                      args ->
                          args.contains("--name")
                              && args.contains(
                                  ProjectTestEnvironments.volume("project") + ":/app/data")))
          .isTrue();
      assertThat(
              commands.stream()
                  .anyMatch(args -> args.get(0).equals("volume") && args.contains("rm")))
          .isFalse();
    } finally {
      environments.close();
    }
  }

  @Test
  void refusesResourcesWhoseOwnershipDoesNotMatch() {
    String unrelated = containerJson("another", true, "start");
    var environments =
        new ProjectTestEnvironments(
            mapper, jdbc, "floci:test", "us-east-1", (args, seconds) -> ok(unrelated));
    try {
      assertThatThrownBy(() -> environments.ensure("project")).hasMessageContaining("not owned");
    } finally {
      environments.close();
    }
  }

  @Test
  void legacyMissingAndRestartedEnvironmentsRequireRedeployment() {
    var environments =
        new ProjectTestEnvironments(
            mapper, jdbc, "floci:test", "us-east-1", (args, seconds) -> missing());
    try {
      ObjectNode live =
          mapper.createObjectNode().put("id", "instance:start").put("status", "RUNNING");
      ObjectNode deployment = mapper.createObjectNode();
      assertThat(environments.matches(deployment, live)).isFalse();
      deployment.set("environment", live.deepCopy().put("mode", ProjectTestEnvironments.MODE));
      assertThat(environments.matches(deployment, live)).isTrue();
      assertThat(environments.matches(deployment, live.deepCopy().put("id", "instance:restarted")))
          .isFalse();
      assertThat(environments.matches(deployment, live.deepCopy().put("status", "STOPPED")))
          .isFalse();
      assertThat(environments.matches(deployment, environments.inspect("project"))).isFalse();
    } finally {
      environments.close();
    }
  }

  @Test
  void cleanupRemovesOnlyDeletedProjectsManagedResources() {
    var commands = new ArrayList<List<String>>();
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("live"))).thenReturn(true);
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("deleted"))).thenReturn(false);
    var environments =
        new ProjectTestEnvironments(
            mapper,
            jdbc,
            "floci:test",
            "us-east-1",
            (args, seconds) -> {
              commands.add(args);
              if (args.get(0).equals("ps")) {
                if (args.contains(
                    "label=floci_namespace=" + ProjectTestEnvironments.container("deleted")))
                  return ok("abcdef123456\n");
                return ok(
                    ProjectTestEnvironments.container("live")
                        + "\n"
                        + ProjectTestEnvironments.container("deleted"));
              }
              if (args.contains("inspect")) {
                String name = args.get(args.size() - 1);
                String project =
                    name.contains(ProjectTestEnvironments.suffix("live")) ? "live" : "deleted";
                if (args.get(0).equals("container"))
                  return ok(containerJson(project, true, "start"));
                return ok(
                    mapper.writeValueAsString(
                        List.of(
                            Map.of(
                                "Labels",
                                Map.of(
                                    ProjectTestEnvironments.OWNER,
                                    "true",
                                    ProjectTestEnvironments.PROJECT,
                                    project)))));
              }
              if (args.contains("ls")) {
                if (args.contains(
                    "label=floci_namespace=" + ProjectTestEnvironments.container("deleted")))
                  return ok(args.get(0).equals("volume") ? "child-data" : "child-network");
                return ok(
                    args.get(0).equals("volume")
                        ? ProjectTestEnvironments.volume("deleted")
                        : ProjectTestEnvironments.network("deleted"));
              }
              return ok("");
            });
    try {
      environments.cleanDeletedProjects();
      assertThat(commands)
          .contains(
              List.of("rm", "-f", ProjectTestEnvironments.container("deleted")),
              List.of("rm", "-f", "-v", "abcdef123456"),
              List.of("volume", "rm", ProjectTestEnvironments.volume("deleted")),
              List.of("volume", "rm", "child-data"),
              List.of("network", "rm", "child-network"),
              List.of("network", "rm", ProjectTestEnvironments.network("deleted")));
      assertThat(commands)
          .doesNotContain(List.of("rm", "-f", ProjectTestEnvironments.container("live")));
    } finally {
      environments.close();
    }
  }

  @Test
  void cleanupRecoversWhenTheEmulatorContainerIsAlreadyGone() {
    var commands = new ArrayList<List<String>>();
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("deleted"))).thenReturn(false);
    var environments =
        new ProjectTestEnvironments(
            mapper,
            jdbc,
            "floci:test",
            "us-east-1",
            (args, seconds) -> {
              commands.add(args);
              if (args.get(0).equals("ps"))
                return ok(
                    args.contains(
                            "label=floci_namespace=" + ProjectTestEnvironments.container("deleted"))
                        ? "abcdef123456"
                        : "");
              if (args.equals(
                  List.of(
                      "network",
                      "ls",
                      "--filter",
                      "label=" + ProjectTestEnvironments.OWNER + "=true",
                      "--format",
                      "{{.Name}}"))) return ok(ProjectTestEnvironments.network("deleted"));
              if (args.contains("inspect"))
                return ok(
                    mapper.writeValueAsString(
                        List.of(
                            Map.of(
                                "Labels",
                                Map.of(
                                    ProjectTestEnvironments.OWNER,
                                    "true",
                                    ProjectTestEnvironments.PROJECT,
                                    "deleted")))));
              return ok("");
            });
    try {
      environments.cleanDeletedProjects();
      assertThat(commands)
          .contains(
              List.of("rm", "-f", "-v", "abcdef123456"),
              List.of("network", "rm", ProjectTestEnvironments.network("deleted")));
    } finally {
      environments.close();
    }
  }
}
