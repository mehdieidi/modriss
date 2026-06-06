package io.mehdieidi.modless.mdecli.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts enough Emfatic declarations to create bootstrap Ecore resources.
 */
public final class StubMetamodelExtractor {

    private static final Pattern NAMESPACE_PATTERN =
            Pattern.compile(
                    "@namespace\\s*\\(\\s*uri=\"([^\"]+)\"\\s*,\\s*prefix=\"([^\"]+)\"\\s*\\)");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "package\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern CLASSIFIER_PATTERN =
            Pattern.compile(
                    "(?m)^\\s*(abstract\\s+class|class|interface|enum|datatype)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern FEATURE_PATTERN =
            Pattern.compile(
                    "(?m)^\\s*(?:readonly\\s+volatile\\s+transient\\s+derived\\s+)?(attr|ref|val)\\s+[^;]*?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;");

    /**
     * Extracts a minimal metamodel definition from an Emfatic file.
     *
     * @param emfaticFile source Emfatic file
     * @return bootstrap metamodel definition
     */
    public StubMetamodelDefinition extract(Path emfaticFile) {
        try {
            String content = Files.readString(emfaticFile);
            Matcher namespaceMatcher = NAMESPACE_PATTERN.matcher(content);
            Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
            if (!namespaceMatcher.find() || !packageMatcher.find()) {
                throw new IllegalStateException("Missing @namespace or package declaration in "
                        + emfaticFile.getFileName());
            }

            List<StubClassifierDefinition> classifiers = extractClassifiers(content);

            return new StubMetamodelDefinition(
                    emfaticFile,
                    packageMatcher.group(1).trim(),
                    namespaceMatcher.group(1).trim(),
                    namespaceMatcher.group(2).trim(),
                    classifiers);
        } catch (IOException ex) {
            throw new UncheckedIOException(
                    "Failed to read Emfatic file for stub extraction: " + emfaticFile, ex);
        }
    }

    /**
     * Extracts classifier declarations and their feature placeholders.
     *
     * @param content Emfatic source
     * @return classifier definitions
     */
    private List<StubClassifierDefinition> extractClassifiers(String content) {
        List<StubClassifierDefinition> classifiers = new ArrayList<>();
        Matcher classifierMatcher = CLASSIFIER_PATTERN.matcher(content);
        while (classifierMatcher.find()) {
            String kind = classifierMatcher.group(1).trim();
            String name = classifierMatcher.group(2).trim();
            int bodyStart = content.indexOf('{', classifierMatcher.end());
            List<StubFeatureDefinition> features = List.of();
            if (bodyStart >= 0 && !"enum".equals(kind) && !"datatype".equals(kind)) {
                int bodyEnd = findMatchingBrace(content, bodyStart);
                if (bodyEnd > bodyStart) {
                    String body = content.substring(bodyStart + 1, bodyEnd);
                    features = extractFeatures(body);
                }
            }
            classifiers.add(new StubClassifierDefinition(kind, name, features));
        }
        return classifiers;
    }

    /**
     * Extracts minimal feature declarations from a classifier body.
     *
     * @param body classifier body
     * @return feature definitions
     */
    private List<StubFeatureDefinition> extractFeatures(String body) {
        List<StubFeatureDefinition> features = new ArrayList<>();
        Matcher featureMatcher = FEATURE_PATTERN.matcher(body);
        while (featureMatcher.find()) {
            features.add(new StubFeatureDefinition(featureMatcher.group(1).trim(),
                    featureMatcher.group(2).trim()));
        }
        return features;
    }

    /**
     * Finds the closing brace paired with an opening classifier brace.
     *
     * @param content   Emfatic source
     * @param bodyStart opening-brace offset
     * @return closing-brace offset, or {@code -1}
     */
    private int findMatchingBrace(String content, int bodyStart) {
        int depth = 0;
        for (int index = bodyStart; index < content.length(); index++) {
            char current = content.charAt(index);
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
}
