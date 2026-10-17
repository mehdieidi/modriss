package io.mehdieidi.modriss.platform.psm;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

/** Validates every archive member before extraction; sizes refer to expanded bytes. */
public final class PsmPackageArchive {
  public static final int MAX_ARCHIVE_BYTES = 10 * 1024 * 1024;
  public static final int MAX_EXPANDED_BYTES = 30 * 1024 * 1024;

  private PsmPackageArchive() {}

  public static Map<String, byte[]> read(byte[] bytes, Set<String> extensions) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_ARCHIVE_BYTES) {
      throw new PlatformException(400, "Each script ZIP must contain files and be at most 10 MB.");
    }
    Map<String, byte[]> files = new LinkedHashMap<>();
    int total = 0;
    int entries = 0;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      java.util.zip.ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (++entries > 500)
          throw new PlatformException(400, "A ZIP may contain at most 500 entries.");
        String name = entry.getName();
        requirePath(entry.isDirectory() ? name.replaceAll("/$", "") : name);
        if (entry.isDirectory()) continue;
        String extension = name.substring(name.lastIndexOf('.') + 1);
        if (!extensions.contains(extension)) {
          throw new PlatformException(400, "Unsupported script file: " + name);
        }
        byte[] content = zip.readNBytes(MAX_EXPANDED_BYTES - total + 1);
        total += content.length;
        if (total > MAX_EXPANDED_BYTES || files.size() >= 250) {
          throw new PlatformException(400, "ZIP exceeds its expanded size or file count limit.");
        }
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(content));
        if (files.putIfAbsent(name, content) != null) {
          throw new PlatformException(400, "Duplicate ZIP path: " + name);
        }
      }
      if (files.isEmpty()) throw new PlatformException(400, "ZIP contains no scripts.");
      return files;
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(
          400, "Could not read ZIP. Supply an unencrypted UTF-8 script archive.");
    }
  }

  public static void requirePath(String name) {
    if (name == null
        || name.isBlank()
        || name.length() > 240
        || name.contains("\\")
        || name.contains(":")
        || name.startsWith("/")
        || name.chars().anyMatch(c -> c < 32)) {
      throw new PlatformException(400, "Invalid package path.");
    }
    for (String segment : name.split("/", -1)) {
      if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
        throw new PlatformException(400, "Package paths must stay inside their ZIP.");
      }
    }
  }

  public static void extract(byte[] bytes, Path root, Set<String> extensions) throws Exception {
    for (var entry : read(bytes, extensions).entrySet()) {
      Path target = root.resolve(entry.getKey()).normalize();
      if (!target.startsWith(root)) throw new PlatformException(400, "Invalid archive path.");
      Files.createDirectories(target.getParent());
      Files.write(target, entry.getValue());
    }
  }
}
