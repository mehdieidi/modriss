package io.mehdieidi.modriss.platform.transformation.application;

import io.mehdieidi.modriss.platform.artifact.application.ArtifactService;
import io.mehdieidi.modriss.platform.identity.application.AuthService;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.model.application.ModelService;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import io.mehdieidi.modriss.platform.project.domain.ProjectRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Shared fixtures for transformation service tests. */
final class PlatformTestFixtures {

  private static final Path REPOSITORY_ROOT = findRepositoryRoot();
  private static final byte[] CLIMATE_CIM_XMI =
      loadBytes(REPOSITORY_ROOT.resolve("mde/samples/cim.xmi"));
  private static final byte[] PIM_XMI = loadBytes(REPOSITORY_ROOT.resolve("mde/samples/pim.xmi"));
  private static final byte[] AWS_PSM_XMI =
      loadBytes(REPOSITORY_ROOT.resolve("mde/samples/psm.xmi"));

  private PlatformTestFixtures() {}

  static byte[] climateCimXmi() {
    return CLIMATE_CIM_XMI.clone();
  }

  static byte[] awsPsmXmi() {
    return AWS_PSM_XMI.clone();
  }

  static byte[] pimXmi() {
    return PIM_XMI.clone();
  }

  static ServiceStack createServices(Path tempDir) {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService auth = new AuthService(store, Duration.ofHours(1));
    ProjectService projects = new ProjectService(store, auth);
    ModelService models = new ModelService(store, projects);
    ArtifactService artifacts = new ArtifactService(store, projects);
    TransformationService transformations = new TransformationService(store, models, artifacts);
    return new ServiceStack(store, auth, projects, models, artifacts, transformations);
  }

  static ServiceStack createServicesWithTransformations(Path tempDir) {
    return createServices(tempDir);
  }

  static AuthenticatedContext registerOwner(
      ServiceStack services, String email, String displayName, String projectName) {
    UserRecord user = services.auth().register(email, "password123", displayName).user();
    ProjectRecord project = services.projects().create(user, projectName, "");
    return new AuthenticatedContext(user, project);
  }

  private static Path findRepositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isDirectory(current.resolve("mde/samples"))
          && Files.isDirectory(current.resolve("mde/metamodels"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Could not locate repository root from user.dir.");
  }

  private static byte[] loadBytes(Path path) {
    try {
      return Files.readAllBytes(path);
    } catch (IOException ex) {
      throw new IllegalStateException("Could not load test fixture: " + path, ex);
    }
  }

  record ServiceStack(
      TestPlatformStore store,
      AuthService auth,
      ProjectService projects,
      ModelService models,
      ArtifactService artifacts,
      TransformationService transformations) {}

  record AuthenticatedContext(UserRecord user, ProjectRecord project) {}
}
