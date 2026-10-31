package io.mehdieidi.modriss.backend.testing;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

/** Reads bounded UTF-8 source archives without extracting untrusted paths to disk. */
public final class RefinedProjectArchive {
  private RefinedProjectArchive() {}

  public static Map<String, String> read(InputStream input) {
    Map<String, String> files = new LinkedHashMap<>();
    int total = 0;
    try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
      java.util.zip.ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;
        String name = entry.getName().replace('\\', '/');
        if (name.startsWith("/")
            || name.contains(":")
            || name.contains("\u0000")
            || java.util.Arrays.asList(name.split("/")).contains("..")) {
          throw new PlatformException(400, "Archive contains an unsafe path.");
        }
        boolean excluded =
            java.util.Arrays.stream(name.split("/"))
                .anyMatch(
                    part ->
                        java.util.Set.of(
                                ".git",
                                "node_modules",
                                ".aws-sam",
                                "bin",
                                "__pycache__",
                                "__MACOSX")
                            .contains(part));
        byte[] bytes = zip.readNBytes(3_000_001);
        total += bytes.length;
        if (bytes.length > 3_000_000 || total > 30_000_000 || files.size() >= 3000) {
          throw new PlatformException(
              413, "Source archive exceeds the 30 MB, 3 MB per file, or 3,000 file limit.");
        }
        if (excluded) continue;
        name = Path.of(name).normalize().toString().replace('\\', '/');
        String content =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        if (content.indexOf('\0') >= 0)
          throw new PlatformException(
              400, "Source archive must contain UTF-8 text files. Exclude compiled binaries.");
        if (files.put(name, content) != null)
          throw new PlatformException(400, "Duplicate file in archive: " + name);
      }
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(
          400,
          "Upload a ZIP of UTF-8 project source files. Exclude binary assets and build output.");
    }
    // ZIP tools commonly wrap projects in one directory; preserve the source paths inside it.
    if (!files.isEmpty()) {
      String first = files.keySet().iterator().next();
      int slash = first.indexOf('/');
      if (slash > 0) {
        String prefix = first.substring(0, slash + 1);
        if (files.keySet().stream().allMatch(path -> path.startsWith(prefix))
            && !java.util.Set.of("scripts/", "src/", "tests/", "infrastructure/")
                .contains(prefix)) {
          Map<String, String> unwrapped = new LinkedHashMap<>();
          files.forEach((path, content) -> unwrapped.put(path.substring(prefix.length()), content));
          return unwrapped;
        }
      }
    }
    return files;
  }
}
