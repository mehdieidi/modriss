package io.mehdieidi.varka.mdecli.service;

import io.mehdieidi.varka.mdecli.diagnostics.DiagnosticEntry;
import io.mehdieidi.varka.mdecli.diagnostics.DiagnosticEntry.Severity;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.emfatic.core.generator.ecore.Builder;
import org.eclipse.emf.emfatic.core.generator.ecore.Connector;
import org.eclipse.emf.emfatic.core.lang.gen.parser.EmfaticParserDriver;
import org.eclipse.emf.emfatic.core.util.EmfaticKeywords;
import org.eclipse.gymnast.runtime.core.parser.ParseContext;
import org.eclipse.gymnast.runtime.core.parser.ParseError;
import org.eclipse.gymnast.runtime.core.parser.ParseMessage;

/**
 * Compiles Emfatic source into an in-memory Ecore resource with actionable diagnostics.
 *
 * <p>The compiler preserves the repository's {@code id attr} extension by normalizing it for the
 * upstream parser and restoring Ecore ID flags after model construction.
 */
public final class EmfaticCompiler {

  /** Matches class-like declarations that can contain ID attributes. */
  private static final Pattern CLASSIFIER_PATTERN =
      Pattern.compile(
          "(?m)^\\s*(abstract\\s+class|class|interface)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");

  /** Matches repository-specific {@code id attr} declarations. */
  private static final Pattern ID_ATTRIBUTE_PATTERN =
      Pattern.compile("(?m)^\\s*id\\s+attr\\s+[^;]*?\\s+([~]?[A-Za-z_][A-Za-z0-9_]*)\\s*;");

  /** Rewrites {@code id attr} declarations into syntax accepted by the upstream parser. */
  private static final Pattern ID_ATTRIBUTE_NORMALIZATION_PATTERN =
      Pattern.compile("(?m)^(\\s*)id\\s+(attr\\s+[^;]*?\\s+)([~]?[A-Za-z_][A-Za-z0-9_]*)(\\s*;)");

  private final EmfResourceSupport resourceSupport = new EmfResourceSupport();

  /**
   * Compiles one Emfatic source file.
   *
   * @param emfaticFile source file
   * @return compiled resource, original source, and mapped diagnostics
   * @throws IOException if the source cannot be read
   */
  public CompilationResult compile(Path emfaticFile) throws IOException {
    String source = Files.readString(emfaticFile);
    NormalizedSource normalizedSource = normalizeSource(source);
    URI uri = URI.createFileURI(emfaticFile.toAbsolutePath().toString());
    ParseContext parseContext =
        new EmfaticParserDriver(uri).parse(new StringReader(normalizedSource.source()));
    List<DiagnosticEntry> diagnostics =
        new ArrayList<>(mapMessages(parseContext, emfaticFile, normalizedSource.source()));

    ResourceSet resourceSet = resourceSupport.newResourceSet();
    Resource resource = resourceSupport.newEcoreResource(resourceSet, uri);
    Builder builder = new Builder();
    builder.build(parseContext, resource, new NullProgressMonitor());
    applyIdMarkers(resource, normalizedSource.idMarkers());
    diagnostics.addAll(
        appendNewMessages(parseContext, emfaticFile, normalizedSource.source(), diagnostics));

    if (!parseContext.hasErrors()) {
      Connector connector = new Connector(builder);
      connector.connect(parseContext, resource, new NullProgressMonitor());
      applyIdMarkers(resource, normalizedSource.idMarkers());
      diagnostics.addAll(
          appendNewMessages(parseContext, emfaticFile, normalizedSource.source(), diagnostics));
    }

    return new CompilationResult(source, resource, diagnostics, parseContext.hasErrors());
  }

  /**
   * Normalizes repository extensions while retaining markers needed after compilation.
   *
   * @param source original Emfatic source
   * @return parser-compatible source and extracted ID markers
   */
  private NormalizedSource normalizeSource(String source) {
    List<IdAttributeMarker> markers = extractIdMarkers(source);
    Matcher matcher = ID_ATTRIBUTE_NORMALIZATION_PATTERN.matcher(source);
    StringBuffer normalized = new StringBuffer();
    while (matcher.find()) {
      String indentation = matcher.group(1);
      String attributePrefix = matcher.group(2);
      String attributeName = matcher.group(3);
      String terminator = matcher.group(4);
      String escapedName = escapeIdentifierIfNeeded(attributeName);
      matcher.appendReplacement(
          normalized,
          Matcher.quoteReplacement(indentation + attributePrefix + escapedName + terminator));
    }
    matcher.appendTail(normalized);
    return new NormalizedSource(normalized.toString(), markers);
  }

  /**
   * Extracts class and attribute pairs declared with {@code id attr}.
   *
   * @param source original Emfatic source
   * @return ID attribute markers
   */
  private List<IdAttributeMarker> extractIdMarkers(String source) {
    List<IdAttributeMarker> markers = new ArrayList<>();
    Matcher classifierMatcher = CLASSIFIER_PATTERN.matcher(source);
    while (classifierMatcher.find()) {
      String classifierName = classifierMatcher.group(2).trim();
      int bodyStart = source.indexOf('{', classifierMatcher.end());
      if (bodyStart < 0) {
        continue;
      }
      int bodyEnd = findMatchingBrace(source, bodyStart);
      if (bodyEnd <= bodyStart) {
        continue;
      }
      String body = source.substring(bodyStart + 1, bodyEnd);
      Matcher idAttributeMatcher = ID_ATTRIBUTE_PATTERN.matcher(body);
      while (idAttributeMatcher.find()) {
        markers.add(
            new IdAttributeMarker(
                classifierName, unescapeIdentifier(idAttributeMatcher.group(1).trim())));
      }
    }
    return markers;
  }

  /**
   * Restores Ecore ID flags removed by source normalization.
   *
   * @param resource compiled Ecore resource
   * @param markers extracted ID attribute markers
   */
  private void applyIdMarkers(Resource resource, List<IdAttributeMarker> markers) {
    if (markers.isEmpty()) {
      return;
    }
    for (IdAttributeMarker marker : markers) {
      EClass eClass = findClass(resource.getContents(), marker.className());
      if (eClass == null) {
        continue;
      }
      EAttribute eAttribute = findAttribute(eClass, marker.attributeName());
      if (eAttribute != null) {
        eAttribute.setID(true);
      }
    }
  }

  /**
   * Finds a class recursively among Ecore resource roots.
   *
   * @param contents Ecore roots
   * @param className class name
   * @return matching class, or {@code null}
   */
  private EClass findClass(List<EObject> contents, String className) {
    for (EObject root : contents) {
      if (root instanceof EPackage ePackage) {
        EClass match = findClass(ePackage, className);
        if (match != null) {
          return match;
        }
      }
    }
    return null;
  }

  /**
   * Finds a class recursively within a package.
   *
   * @param ePackage package to search
   * @param className class name
   * @return matching class, or {@code null}
   */
  private EClass findClass(EPackage ePackage, String className) {
    for (EClassifier classifier : ePackage.getEClassifiers()) {
      if (classifier instanceof EClass eClass && className.equals(eClass.getName())) {
        return eClass;
      }
    }
    for (EPackage subpackage : ePackage.getESubpackages()) {
      EClass match = findClass(subpackage, className);
      if (match != null) {
        return match;
      }
    }
    return null;
  }

  /**
   * Finds an inherited or declared attribute by name.
   *
   * @param eClass class to search
   * @param attributeName attribute name
   * @return matching attribute, or {@code null}
   */
  private EAttribute findAttribute(EClass eClass, String attributeName) {
    return eClass.getEAllAttributes().stream()
        .filter(attribute -> attributeName.equals(attribute.getName()))
        .findFirst()
        .orElse(null);
  }

  /**
   * Finds the closing brace paired with an opening classifier brace.
   *
   * @param source Emfatic source
   * @param bodyStart opening-brace offset
   * @return closing-brace offset, or {@code -1}
   */
  private int findMatchingBrace(String source, int bodyStart) {
    int depth = 0;
    for (int index = bodyStart; index < source.length(); index++) {
      char current = source.charAt(index);
      if (current == '{') {
        depth++;
      } else if (current == '}') {
        depth--;
        if (depth == 0) {
          return index;
        }
      }
    }
    return -1;
  }

  /**
   * Removes Emfatic's leading keyword-escape marker.
   *
   * @param identifier identifier to unescape
   * @return bare identifier
   */
  private String unescapeIdentifier(String identifier) {
    return identifier.startsWith("~") ? identifier.substring(1) : identifier;
  }

  /**
   * Escapes a keyword identifier when required by the upstream parser.
   *
   * @param identifier source identifier
   * @return parser-safe identifier
   */
  private String escapeIdentifierIfNeeded(String identifier) {
    String bareIdentifier = unescapeIdentifier(identifier);
    if (identifier.startsWith("~")) {
      return identifier;
    }
    if (EmfaticKeywords.IsKeyword(bareIdentifier)
        && !"true".equals(bareIdentifier)
        && !"false".equals(bareIdentifier)) {
      return "~" + bareIdentifier;
    }
    return bareIdentifier;
  }

  /**
   * Returns parser messages added after a previous build or connect phase.
   *
   * @param parseContext parser context
   * @param emfaticFile source file
   * @param source normalized source
   * @param alreadyCollected previously mapped diagnostics
   * @return newly added diagnostics
   */
  private List<DiagnosticEntry> appendNewMessages(
      ParseContext parseContext,
      Path emfaticFile,
      String source,
      List<DiagnosticEntry> alreadyCollected) {
    List<DiagnosticEntry> all = mapMessages(parseContext, emfaticFile, source);
    if (all.size() <= alreadyCollected.size()) {
      return List.of();
    }
    return all.subList(alreadyCollected.size(), all.size());
  }

  /**
   * Maps parser messages to source-aware CLI diagnostics.
   *
   * @param parseContext parser context
   * @param emfaticFile source file
   * @param source normalized source
   * @return mapped diagnostics
   */
  private List<DiagnosticEntry> mapMessages(
      ParseContext parseContext, Path emfaticFile, String source) {
    SourceDocument document = new SourceDocument(source);
    List<DiagnosticEntry> diagnostics = new ArrayList<>();
    for (ParseMessage message : parseContext.getMessages()) {
      SourceLocation sourceLocation = document.locate(message.getOffset(), message.getLength());
      Severity severity = message instanceof ParseError ? Severity.ERROR : Severity.WARNING;
      String nextLineText = document.line(sourceLocation.line() + 1);
      diagnostics.add(
          new DiagnosticEntry(
              severity,
              message.getMessage(),
              emfaticFile.toString(),
              sourceLocation.line(),
              sourceLocation.column(),
              hintForMessage(message.getMessage(), sourceLocation.lineText(), nextLineText),
              sourceLocation.rendered()));
    }
    return diagnostics;
  }

  /**
   * Derives actionable guidance from a parser message and nearby source lines.
   *
   * @param message parser message
   * @param lineText reported source line
   * @param nextLineText following source line
   * @return resolution hint, or an empty string
   */
  private String hintForMessage(String message, String lineText, String nextLineText) {
    if (lineText != null) {
      String reservedIdentifierHint = reservedKeywordIdentifierHint(lineText);
      if (!reservedIdentifierHint.isBlank()) {
        return reservedIdentifierHint;
      }
    }
    if (nextLineText != null) {
      String reservedIdentifierHint = reservedKeywordIdentifierHint(nextLineText);
      if (!reservedIdentifierHint.isBlank()) {
        return reservedIdentifierHint;
      }
    }
    if (message == null || message.isBlank()) {
      return "";
    }
    String normalized = message.toLowerCase();
    if (normalized.contains("failed to resolve type")) {
      return "Check the referenced type name and imported package prefix. In modular metamodels,"
          + " confirm the sibling module is present.";
    }
    if (normalized.contains("failed to load import model")) {
      return "The imported .ecore file could not be loaded. Make sure the file exists and the"
          + " import path is correct relative to the current .emf file.";
    }
    if (normalized.contains("encountered")) {
      return "This is an Emfatic parse error. Inspect the shown line and the tokens immediately"
          + " before the reported position.";
    }
    return "";
  }

  /**
   * Detects unescaped Emfatic keywords used as feature identifiers.
   *
   * @param lineText source line
   * @return keyword escape hint, or an empty string
   */
  private String reservedKeywordIdentifierHint(String lineText) {
    String trimmed = lineText.strip();
    String[] prefixes = {
      "attr ", "id attr ", "ref ", "val ", "readonly ", "volatile ", "transient ", "derived "
    };
    boolean looksLikeFeatureLine = false;
    for (String prefix : prefixes) {
      if (trimmed.startsWith(prefix)) {
        looksLikeFeatureLine = true;
        break;
      }
    }
    if (!looksLikeFeatureLine) {
      return "";
    }

    String normalized =
        trimmed.replace("[", " ").replace("]", " ").replace(";", " ").replace("#", " ");
    String[] tokens = normalized.split("\\s+");
    for (int index = 2; index < tokens.length; index++) {
      String token = tokens[index];
      if (EmfaticKeywords.IsKeyword(token) && !"true".equals(token) && !"false".equals(token)) {
        return "The identifier `"
            + token
            + "` is an Emfatic keyword. Rename it or escape it as `~"
            + token
            + "`.";
      }
    }
    return "";
  }

  /**
   * Result of compiling one Emfatic source file.
   *
   * @param source original source text
   * @param resource compiled Ecore resource
   * @param diagnostics mapped parser diagnostics
   * @param hasErrors whether parser errors remain
   */
  public record CompilationResult(
      String source, Resource resource, List<DiagnosticEntry> diagnostics, boolean hasErrors) {

    /**
     * Returns the first compiled root as an Ecore package.
     *
     * @return root package, or {@code null} when no roots were produced
     */
    public EPackage rootPackage() {
      if (resource.getContents().isEmpty()) {
        return null;
      }
      return (EPackage) resource.getContents().get(0);
    }
  }

  /**
   * Source coordinates and excerpt information for one parser message.
   *
   * @param line one-based line
   * @param column one-based column
   * @param lineText source line text
   * @param caretColumn one-based caret position within the source line
   */
  private record SourceLocation(int line, int column, String lineText, int caretColumn) {

    /**
     * Renders the source line and a caret pointing at the diagnostic location.
     *
     * @return rendered excerpt, or an empty string
     */
    String rendered() {
      if (lineText == null || lineText.isBlank()) {
        return "";
      }
      return lineText + System.lineSeparator() + " ".repeat(Math.max(0, caretColumn - 1)) + "^";
    }
  }

  /** Indexed source document used to map parser offsets to line and column coordinates. */
  private static final class SourceDocument {

    private final List<String> lines;
    private final int[] offsets;

    /**
     * Indexes source lines and their absolute starting offsets.
     *
     * @param source normalized source text
     */
    private SourceDocument(String source) {
      List<String> resolvedLines = new ArrayList<>();
      List<Integer> resolvedOffsets = new ArrayList<>();
      int offset = 0;
      int lineStart = 0;
      for (int index = 0; index < source.length(); index++) {
        char current = source.charAt(index);
        if (current == '\r' || current == '\n') {
          resolvedOffsets.add(lineStart);
          resolvedLines.add(source.substring(lineStart, index));
          if (current == '\r' && index + 1 < source.length() && source.charAt(index + 1) == '\n') {
            index++;
          }
          lineStart = index + 1;
          offset = lineStart;
        }
      }
      if (lineStart <= source.length()) {
        resolvedOffsets.add(lineStart);
        resolvedLines.add(source.substring(lineStart));
      }
      this.lines = resolvedLines;
      this.offsets = resolvedOffsets.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Maps an absolute parser offset to a source location.
     *
     * @param offset absolute source offset
     * @param length parser message span length
     * @return mapped source location
     */
    private SourceLocation locate(int offset, int length) {
      if (lines.isEmpty()) {
        return new SourceLocation(-1, -1, "", -1);
      }
      int safeOffset = Math.max(0, offset);
      int lineIndex = 0;
      for (int index = 0; index < offsets.length; index++) {
        if (offsets[index] <= safeOffset) {
          lineIndex = index;
        } else {
          break;
        }
      }
      String lineText = lines.get(lineIndex);
      int column = Math.max(1, safeOffset - offsets[lineIndex] + 1);
      int caretColumn = Math.min(Math.max(1, column), lineText.length() + 1);
      return new SourceLocation(lineIndex + 1, column, lineText, caretColumn);
    }

    /**
     * Returns a source line by one-based line number.
     *
     * @param oneBasedLineNumber one-based line number
     * @return source line, or {@code null} when out of range
     */
    private String line(int oneBasedLineNumber) {
      if (oneBasedLineNumber < 1 || oneBasedLineNumber > lines.size()) {
        return null;
      }
      return lines.get(oneBasedLineNumber - 1);
    }
  }

  /**
   * Parser-compatible source and metadata needed to restore repository extensions.
   *
   * @param source normalized source
   * @param idMarkers extracted ID attribute markers
   */
  private record NormalizedSource(String source, List<IdAttributeMarker> idMarkers) {}

  /**
   * Identifies an attribute that must be marked as an Ecore ID after compilation.
   *
   * @param className declaring class name
   * @param attributeName attribute name
   */
  private record IdAttributeMarker(String className, String attributeName) {}
}
