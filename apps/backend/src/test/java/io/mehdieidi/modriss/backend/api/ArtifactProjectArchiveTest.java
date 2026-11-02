package io.mehdieidi.modriss.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ArtifactProjectArchiveTest {

  @Test
  void readsTextFilesAndEmptyDirectoriesAndSkipsBuildOutput() throws Exception {
    var archive =
        ArtifactProjectArchive.read(
            archive(
                Map.of(
                    "project/src/handler.go", "package main",
                    "project/.aws-sam/build/app", "compiled",
                    "project/src/empty/", "")));

    assertThat(archive.files())
        .containsOnlyKeys("project/src/handler.go")
        .containsEntry("project/src/handler.go", "package main");
    assertThat(archive.directories()).contains("project/src/empty");
  }

  @Test
  void rejectsUnsafeDuplicateAndBinaryEntries() throws Exception {
    assertThatThrownBy(() -> ArtifactProjectArchive.read(archive(Map.of("../outside", "x"))))
        .isInstanceOf(PlatformException.class);
    assertThatThrownBy(
            () ->
                ArtifactProjectArchive.read(
                    archiveEntries(Map.of("src/file.txt", "first", "src\\file.txt", "second"))))
        .isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> ArtifactProjectArchive.read(archive(Map.of("src/file", "\0"))))
        .isInstanceOf(PlatformException.class);
  }

  private ByteArrayInputStream archive(Map<String, String> files) throws Exception {
    return archiveEntries(files);
  }

  private ByteArrayInputStream archiveEntries(Map<String, String> entries) throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      for (var entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return new ByteArrayInputStream(bytes.toByteArray());
  }
}
