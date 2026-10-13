package io.mehdieidi.modriss.platform.transformation.synchronization;

/** Feature ownership policy used by the synchronization engine. */
public enum MergeFeaturePolicy {
  THREE_WAY,
  GENERATOR_OWNED,
  USER_OWNED,
  IGNORE,
  IMMUTABLE
}
