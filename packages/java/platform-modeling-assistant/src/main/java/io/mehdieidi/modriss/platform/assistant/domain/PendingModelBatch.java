package io.mehdieidi.modriss.platform.assistant.domain;

/** An LLM-authored destructive proposal bound to the model revision shown before confirmation. */
public record PendingModelBatch(ModelCommandBatch batch, long baseRevision) {}
