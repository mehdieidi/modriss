package io.mehdieidi.modriss.platform.artifact.storage;

import io.mehdieidi.modriss.platform.artifact.domain.ArtifactRecord;
import java.util.List;
import java.util.Optional;

/**
 * Optional optimized persistence operations for generated artifacts.
 *
 * <p>Implementations can avoid loading every artifact file when callers only need metadata, one
 * file, or archive entries. The application service keeps a record-based fallback for simpler
 * stores and tests.
 */
public interface ArtifactStorage {

  /** Lists artifact metadata and file paths without loading file contents. */
  List<ArtifactRecord> listSummaries(String projectId);

  /** Reads one artifact file without materializing the rest of the artifact. */
  Optional<String> readFile(String artifactId, String path);

  /** Reads all files for one artifact for archive generation. */
  List<ArtifactFile> listFiles(String artifactId);

  /** One file entry returned by an optimized artifact store. */
  record ArtifactFile(String path, String content) {}
}
