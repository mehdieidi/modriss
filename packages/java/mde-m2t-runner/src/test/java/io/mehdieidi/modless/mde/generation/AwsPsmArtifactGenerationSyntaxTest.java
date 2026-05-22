package io.mehdieidi.modless.mde.generation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.egl.EglModule;
import org.junit.jupiter.api.Test;

final class AwsPsmArtifactGenerationSyntaxTest {

    private static final Path REPOSITORY_ROOT = Path.of("").toAbsolutePath()
            .getParent()
            .getParent()
            .getParent();

    private static final Path GENERATOR_ROOT = REPOSITORY_ROOT.resolve(
            "mde/generation/awspsm-to-artifacts");

    private static String describeProblems(Path file, Iterable<ParseProblem> problems) {
        StringBuilder builder = new StringBuilder("Parse problems in ")
                .append(file)
                .append(':');
        for (ParseProblem problem : problems) {
            builder.append(System.lineSeparator())
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

    @Test
    void egxCoordinatorReferencesExistingImportsAndTemplates() throws Exception {
        Path egxFile = GENERATOR_ROOT.resolve("awspsm2artifacts.egx");
        String egxText = Files.readString(egxFile);

        Matcher importMatcher = Pattern.compile("(?m)^import\\s+\"([^\"]+)\"").matcher(egxText);
        while (importMatcher.find()) {
            assertTrue(Files.isRegularFile(GENERATOR_ROOT.resolve(importMatcher.group(1))),
                    () -> "Missing EGX import " + importMatcher.group(1));
        }

        Matcher templateMatcher = Pattern.compile("template\\s*:\\s*\"([^\"]+)\"").matcher(egxText);
        while (templateMatcher.find()) {
            assertTrue(Files.isRegularFile(GENERATOR_ROOT.resolve("templates")
                            .resolve(templateMatcher.group(1))),
                    () -> "Missing EGX template " + templateMatcher.group(1));
        }
    }

    @Test
    void everyEglTemplateParsesWithoutErrors() throws Exception {
        try (Stream<Path> templateFiles = Files.walk(GENERATOR_ROOT.resolve("templates"))) {
            for (Path templateFile : templateFiles
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
                assertTrue(module.getParseProblems().stream()
                                .noneMatch(problem -> problem.getSeverity() == ParseProblem.ERROR),
                        () -> describeProblems(templateFile, module.getParseProblems()));
            }
        }
    }
}
