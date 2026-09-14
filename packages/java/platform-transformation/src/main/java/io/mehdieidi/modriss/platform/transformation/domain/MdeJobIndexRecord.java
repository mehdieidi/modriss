package io.mehdieidi.modriss.platform.transformation.domain;

/**
 * Global lookup entry mapping an MDE job id to its owning project.
 *
 * @param jobId job identifier
 * @param projectId owning project identifier
 */
public record MdeJobIndexRecord(String jobId, String projectId) {}
