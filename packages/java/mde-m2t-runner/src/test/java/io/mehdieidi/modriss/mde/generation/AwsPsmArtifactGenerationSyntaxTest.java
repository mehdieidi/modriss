package io.mehdieidi.modriss.mde.generation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.egl.EglModule;
import org.junit.jupiter.api.Test;

/** Syntax-level regression tests for the AWS PSM artifact generation templates. */
final class AwsPsmArtifactGenerationSyntaxTest {

  /** Repository root derived from the Maven module working directory. */
  private static final Path REPOSITORY_ROOT =
      Path.of("").toAbsolutePath().getParent().getParent().getParent();

  /** Root directory containing the EGX coordinator and EGL templates. */
  private static final Path GENERATOR_ROOT =
      REPOSITORY_ROOT.resolve("mde/generation/awspsm-to-artifacts");

  /** Coverage report that inventories the generator surface. */
  private static final Path COVERAGE_REPORT =
      REPOSITORY_ROOT.resolve("docs/internal/artifacts/aws-psm-code-generator-coverage.md");

  /**
   * Formats EGL parse problems into an assertion message tied to the template file being parsed.
   *
   * @param file template file with parse problems
   * @param problems parse problems returned by Epsilon
   * @return multi-line assertion message
   */
  private static String describeProblems(Path file, Iterable<ParseProblem> problems) {
    StringBuilder builder = new StringBuilder("Parse problems in ").append(file).append(':');
    for (ParseProblem problem : problems) {
      builder
          .append(System.lineSeparator())
          .append(problem.getLine())
          .append(':')
          .append(problem.getColumn())
          .append(" [")
          .append(problem.getSeverity())
          .append("] ")
          .append(problem.getReason());
    }
    return builder.toString();
  }

  /**
   * Ensures the EGX coordinator only imports existing modules and references existing templates.
   *
   * @throws Exception when the EGX coordinator cannot be read
   */
  @Test
  void egxCoordinatorReferencesExistingImportsAndTemplates() throws Exception {
    Path egxFile = GENERATOR_ROOT.resolve("awspsm2artifacts.egx");
    String egxText = Files.readString(egxFile);

    Matcher importMatcher = Pattern.compile("(?m)^import\\s+\"([^\"]+)\"").matcher(egxText);
    while (importMatcher.find()) {
      assertTrue(
          Files.isRegularFile(GENERATOR_ROOT.resolve(importMatcher.group(1))),
          () -> "Missing EGX import " + importMatcher.group(1));
    }

    Matcher templateMatcher = Pattern.compile("template\\s*:\\s*\"([^\"]+)\"").matcher(egxText);
    while (templateMatcher.find()) {
      assertTrue(
          Files.isRegularFile(
              GENERATOR_ROOT.resolve("templates").resolve(templateMatcher.group(1))),
          () -> "Missing EGX template " + templateMatcher.group(1));
    }
  }

  /**
   * Parses every EGL template to catch syntax errors before generation tests execute them.
   *
   * @throws Exception when template discovery fails
   */
  @Test
  void everyEglTemplateParsesWithoutErrors() throws Exception {
    try (Stream<Path> templateFiles = Files.walk(GENERATOR_ROOT.resolve("templates"))) {
      for (Path templateFile :
          templateFiles
              .filter(path -> path.getFileName().toString().endsWith(".egl"))
              .sorted()
              .toList()) {
        EglModule module = new EglModule();

        boolean parsed;
        try {
          parsed = module.parse(templateFile.toFile());
        } catch (Exception ex) {
          fail("Could not parse " + templateFile + ": " + ex.getMessage(), ex);
          return;
        }

        assertTrue(parsed, () -> describeProblems(templateFile, module.getParseProblems()));
        assertTrue(
            module.getParseProblems().stream()
                .noneMatch(problem -> problem.getSeverity() == ParseProblem.ERROR),
            () -> describeProblems(templateFile, module.getParseProblems()));
      }
    }
  }

  /**
   * Fails when a new EGL template is added without being inventoried in the coverage report.
   *
   * @throws Exception when template discovery or report reading fails
   */
  @Test
  void coverageReportListsEveryEglTemplate() throws Exception {
    String coverageText = Files.readString(COVERAGE_REPORT);
    for (String templatePath : eglTemplatePaths()) {
      assertTrue(
          coverageText.contains("`" + templatePath + "`"),
          () -> "Coverage report missing EGL template " + templatePath);
    }
  }

  /**
   * Fails when a new EOL operation/helper is added without being inventoried in the coverage
   * report.
   *
   * @throws Exception when EOL discovery or report reading fails
   */
  @Test
  void coverageReportListsEveryEolOperation() throws Exception {
    String coverageText = Files.readString(COVERAGE_REPORT);
    for (String operationId : eolOperationIds()) {
      assertTrue(
          coverageText.contains("`" + operationId + "`"),
          () -> "Coverage report missing EOL operation " + operationId);
    }
  }

  /**
   * Returns generator-relative EGL template paths.
   *
   * @return sorted template paths using slash separators
   * @throws IOException when template discovery fails
   */
  private List<String> eglTemplatePaths() throws IOException {
    try (Stream<Path> templateFiles = Files.walk(GENERATOR_ROOT.resolve("templates"))) {
      return templateFiles
          .filter(path -> path.getFileName().toString().endsWith(".egl"))
          .map(path -> GENERATOR_ROOT.resolve("templates").relativize(path))
          .map(path -> path.toString().replace('\\', '/'))
          .sorted()
          .toList();
    }
  }

  /**
   * Returns stable ids for every EOL operation declaration.
   *
   * @return sorted operation ids in {@code file.eol:Context.operation} form
   * @throws IOException when helper discovery fails
   */
  private List<String> eolOperationIds() throws IOException {
    Pattern operationPattern = Pattern.compile("^operation\\s+(?:(\\S+)\\s+)?(\\w+)\\s*\\(");
    List<String> operationIds = new ArrayList<>();
    try (Stream<Path> eolFiles = Files.walk(GENERATOR_ROOT.resolve("lib"))) {
      for (Path eolFile :
          eolFiles
              .filter(path -> path.getFileName().toString().endsWith(".eol"))
              .sorted()
              .toList()) {
        String libraryName = GENERATOR_ROOT.resolve("lib").relativize(eolFile).toString();
        for (String line : Files.readAllLines(eolFile)) {
          Matcher matcher = operationPattern.matcher(line);
          if (matcher.find()) {
            String context = matcher.group(1);
            String operationName = matcher.group(2);
            operationIds.add(
                libraryName.replace('\\', '/')
                    + ":"
                    + (context == null ? "" : context + ".")
                    + operationName);
          }
        }
      }
    }
    return operationIds.stream().sorted().toList();
  }
}
