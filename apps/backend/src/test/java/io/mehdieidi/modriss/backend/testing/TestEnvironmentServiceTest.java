package io.mehdieidi.modriss.backend.testing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.mehdieidi.modriss.platform.artifact.application.ArtifactService;
import io.mehdieidi.modriss.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import io.mehdieidi.modriss.platform.project.domain.ProjectRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class TestEnvironmentServiceTest {
  private final ArtifactService artifacts = mock(ArtifactService.class);
  private final ProjectService projects = mock(ProjectService.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final ProjectTestEnvironments environments = mock(ProjectTestEnvironments.class);
  private final UserRecord user = mock(UserRecord.class);
  private final ProjectRecord project = mock(ProjectRecord.class);
  private TestEnvironmentService service;

  @BeforeEach
  void setup() {
    when(user.id()).thenReturn("user");
    when(projects.get(user, "project")).thenReturn(project);
    service =
        new TestEnvironmentService(
            artifacts, projects, jdbc, mapper, "unused", environments, "us-east-1");
  }

  @AfterEach
  void close() {
    service.close();
  }

  @Test
  void viewersCannotDeployOrRunCode() {
    doThrow(new PlatformException(403, "Read-only project member"))
        .when(projects)
        .requireEditor(project, "user");
    assertThatThrownBy(
            () -> service.submit(user, "project", mapper.createObjectNode().put("kind", "DEPLOY")))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("Read-only");
    verifyNoInteractions(artifacts, jdbc);
  }

  @Test
  void rejectsAnotherProjectsArtifactBeforeQueuingWork() {
    var artifact = mock(ArtifactRecord.class);
    when(artifact.projectId()).thenReturn("another-project");
    when(artifacts.get(user, "artifact")).thenReturn(artifact);
    assertThatThrownBy(
            () ->
                service.submit(
                    user,
                    "project",
                    mapper.createObjectNode().put("kind", "DEPLOY").put("artifactId", "artifact")))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("this project's");
    verifyNoInteractions(jdbc);
  }

  @Test
  void uploadedProvidersCannotUseTheAwsEmulator() {
    var artifact = mock(ArtifactRecord.class);
    when(artifact.projectId()).thenReturn("project");
    when(artifact.modelJson())
        .thenReturn(mapper.createObjectNode().put("psmPackageId", "custom-azure"));
    when(artifacts.get(user, "artifact")).thenReturn(artifact);
    assertThatThrownBy(
            () ->
                service.submit(
                    user,
                    "project",
                    mapper.createObjectNode().put("kind", "DEPLOY").put("artifactId", "artifact")))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("built-in AWS");
    verifyNoInteractions(jdbc);
  }

  @Test
  void projectMembershipIsRequiredToReadEvidence() {
    when(projects.get(user, "project"))
        .thenThrow(new PlatformException(403, "Membership required"));
    assertThatThrownBy(() -> service.get(user, "project", "job"))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("Membership");
    verifyNoInteractions(jdbc);
  }

  @Test
  @SuppressWarnings("unchecked")
  void oldSharedEmulatorDeploymentsCannotQueueAnExecution() {
    var legacy =
        mapper
            .createObjectNode()
            .put("id", "deployment")
            .put("kind", "DEPLOY")
            .put("status", "SUCCEEDED");
    doReturn(java.util.List.of(legacy))
        .when(jdbc)
        .query(
            anyString(),
            any(org.springframework.jdbc.core.RowMapper.class),
            eq("project"),
            eq("deployment"));
    when(environments.inspect("project"))
        .thenReturn(mapper.createObjectNode().put("status", "MISSING"));
    assertThatThrownBy(
            () ->
                service.submit(
                    user,
                    "project",
                    mapper
                        .createObjectNode()
                        .put("kind", "EXECUTE")
                        .put("deploymentId", "deployment")
                        .put("resourceId", "Api")
                        .put("action", "request")))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("dedicated Floci environment");
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }
}
