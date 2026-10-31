package io.mehdieidi.modriss.backend.testing;

import static org.assertj.core.api.Assertions.*;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class RefinedProjectArchiveTest {
  @Test
  void unwrapsProjectDirectoryAndExcludesBuildOutputs() throws Exception {
    var files =
        RefinedProjectArchive.read(
            archive(
                Map.of(
                    "project/template.yaml",
                    "Resources: {}",
                    "project/src/handler.go",
                    "package main",
                    "project/bin/function/bootstrap",
                    "binary",
                    "project/.git/config",
                    "private")));
    assertThat(files).containsOnlyKeys("template.yaml", "src/handler.go");
  }

  @Test
  void rejectsTraversalAndBinaryFiles() throws Exception {
    assertThatThrownBy(() -> RefinedProjectArchive.read(archive(Map.of("../outside", "x"))))
        .isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> RefinedProjectArchive.read(archive(Map.of("src/binary", "\0"))))
        .isInstanceOf(PlatformException.class);
  }

  @Test
  void stableSourceFingerprintChangesWhenAnySavedFileChanges() {
    assertThat(TestEnvironmentService.sourceHash(Map.of("b", "2", "a", "1")))
        .isEqualTo(TestEnvironmentService.sourceHash(Map.of("a", "1", "b", "2")))
        .isNotEqualTo(TestEnvironmentService.sourceHash(Map.of("a", "changed", "b", "2")));
    assertThat(TestEnvironmentService.account("project-a"))
        .matches("[0-9]{12}")
        .isNotEqualTo(TestEnvironmentService.account("project-b"));
  }

  private ByteArrayInputStream archive(Map<String, String> files) throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes)) {
      for (var entry : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return new ByteArrayInputStream(bytes.toByteArray());
  }
}
