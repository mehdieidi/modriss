package io.mehdieidi.modless.platform.artifact.domain;

/**
 * Global lookup entry mapping an artifact id to its owning project.
 *
 * @param artifactId artifact identifier
 * @param projectId owning project identifier
 */
public record ArtifactIndexRecord(String artifactId, String projectId) {}
