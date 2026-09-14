package io.mehdieidi.modriss.mdecli.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts declared Ecore imports from Emfatic source files. */
public final class EmfaticImportScanner {

  /** Matches quoted Ecore imports in Emfatic syntax. */
  private static final Pattern IMPORT_PATTERN =
      Pattern.compile("import\\s+\"([^\"]+\\.ecore)\"\\s*;");

  /**
   * Scans one Emfatic file for imported Ecore paths.
   *
   * @param emfaticFile source Emfatic file
   * @return module descriptor
   */
  public ModuleDescriptor scan(Path emfaticFile) {
    try {
      String content = Files.readString(emfaticFile);
      Matcher matcher = IMPORT_PATTERN.matcher(content);
      List<String> imports = matcher.results().map(match -> match.group(1)).toList();
      return new ModuleDescriptor(emfaticFile, imports);
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to read Emfatic file: " + emfaticFile, ex);
    }
  }
}
