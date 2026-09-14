package io.mehdieidi.modriss.mdecli.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Applies CLI conventions for output paths and modular root-file selection. */
public final class PathDecider {

  /**
   * Resolves the output path from an explicit option or the input naming convention.
   *
   * @param request conversion request
   * @return normalized output path
   */
  public Path resolveOutput(ConversionRequest request) {
    if (request.output() != null) {
      return request.output().toAbsolutePath().normalize();
    }

    Path input = request.input().toAbsolutePath().normalize();
    if (Files.isDirectory(input)) {
      return input.resolve(input.getFileName().toString() + "-combined.ecore");
    }

    String fileName = input.getFileName().toString();
    int extensionIndex = fileName.lastIndexOf('.');
    String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
    return input.resolveSibling(baseName + ".ecore");
  }

  /**
   * Resolves a directory conversion's root module.
   *
   * @param directory module directory
   * @param rootOverride optional explicit root module
   * @return normalized root module path
   * @throws IllegalStateException when no unambiguous root can be selected
   */
  public Path resolveRootFile(Path directory, Path rootOverride) {
    if (rootOverride != null) {
      return rootOverride.toAbsolutePath().normalize();
    }

    Path exactRoot = directory.resolve("root.emf");
    if (Files.exists(exactRoot)) {
      return exactRoot;
    }

    List<Path> rootCandidates;
    try (var stream = Files.list(directory)) {
      rootCandidates =
          stream
              .filter(Files::isRegularFile)
              .filter(
                  path -> {
                    String name = path.getFileName().toString();
                    return name.endsWith("-root.emf") || name.endsWith("-root.emfatic");
                  })
              .sorted(Comparator.naturalOrder())
              .toList();
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to inspect directory: " + directory, ex);
    }

    if (rootCandidates.size() == 1) {
      return rootCandidates.get(0);
    }
    throw new IllegalStateException(
        "Could not determine the root Emfatic file. Supply it explicitly with --root.");
  }
}
