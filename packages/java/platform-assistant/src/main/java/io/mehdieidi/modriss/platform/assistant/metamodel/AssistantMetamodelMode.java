package io.mehdieidi.modriss.platform.assistant.metamodel;

/** Metamodel surface exposed to the LLM-based modeling assistant. */
public enum AssistantMetamodelMode {
  /** Expose the complete canonical CIM and PIM metamodels. */
  NORMAL,
  /** Expose the curated, high-value CIM and PIM subset. */
  EXCERPT
}
