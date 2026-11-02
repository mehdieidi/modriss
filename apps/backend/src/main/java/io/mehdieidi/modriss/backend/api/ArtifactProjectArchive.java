package io.mehdieidi.modriss.backend.api;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

/** Reads bounded UTF-8 artifact project archives without extracting untrusted paths to disk. */
public final class ArtifactProjectArchive {

  private static final int MAX_FILES = 3_000;
  private static final int MAX_FILE_BYTES = 3_000_000;
  private static final int MAX_TOTAL_BYTES = 30_000_000;
  private static final int MAX_ENTRIES = 6_000;
  private static final Set<String> EXCLUDED_PARTS =
      Set.of(
          ".git",
          "node_modules",
          ".aws-sam",
          "bin",
          "target",
          "dist",
          "build",
          "coverage",
          "__pycache__",
          "__MACOSX");

  private ArtifactProjectArchive() {}

  /** Reads source files and explicit directories from a ZIP archive. */
  public static Contents read(InputStream input) {
    Map<String, String> files = new LinkedHashMap<>();
    Set<String> directories = new LinkedHashSet<>();
    int totalBytes = 0;
    int fileCount = 0;
    int entryCount = 0;
    try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
      java.util.zip.ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (++entryCount > MAX_ENTRIES) {
          throw new PlatformException(413, "Artifact ZIP contains too many entries.");
        }
        String rawName = entry.getName().replace('\\', '/');
        boolean directory = entry.isDirectory() || rawName.endsWith("/");
        String name = directory ? rawName.replaceFirst("/+\\z", "") : rawName;
        validatePath(name);
        boolean excluded =
            Arrays.stream(name.split("/"))
                .anyMatch(part -> EXCLUDED_PARTS.contains(part) || part.equals(".DS_Store"));

        if (directory) {
          if (!excluded) directories.add(name);
          continue;
        }

        fileCount++;
        byte[] bytes = zip.readNBytes(MAX_FILE_BYTES + 1);
        totalBytes += bytes.length;
        if (bytes.length > MAX_FILE_BYTES || totalBytes > MAX_TOTAL_BYTES || fileCount > MAX_FILES) {
          throw new PlatformException(
              413, "Artifact ZIP exceeds the 30 MB, 3 MB per file, or 3,000 file limit.");
        }
        if (excluded) continue;
        String content =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        if (content.indexOf('\0') >= 0) {
          throw new PlatformException(
              400, "Artifact ZIP must contain UTF-8 text files. Exclude compiled binaries.");
        }
        if (files.putIfAbsent(name, content) != null) {
          throw new PlatformException(400, "Duplicate file in artifact ZIP: " + name);
        }
      }
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(
          400, "Upload a ZIP of UTF-8 artifact project files. Exclude binary assets and build output.");
    }
    if (files.isEmpty()) {
      throw new PlatformException(400, "Artifact ZIP contains no source files.");
    }
    return new Contents(files, directories);
  }

  private static void validatePath(String name) {
    if (name == null
        || name.isBlank()
        || name.length() > 1_024
        || name.startsWith("/")
        || name.matches("^[A-Za-z]:.*")
        || name.indexOf('\0') >= 0
        || name.indexOf(':') >= 0) {
      throw new PlatformException(400, "Artifact ZIP contains an unsafe path.");
    }
    for (String segment : name.split("/", -1)) {
      if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
        throw new PlatformException(400, "Artifact ZIP contains an unsafe path.");
      }
    }
  }

  /** Parsed archive contents; directory paths do not include a trailing slash. */
  public record Contents(Map<String, String> files, Set<String> directories) {}
}
