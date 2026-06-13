package io.mehdieidi.modless.mde.etl;

import java.util.UUID;

/** Generates globally unique identifiers for model elements created by ETL transformations. */
public final class ModelElementIdGenerator {

  /**
   * Generates the next model element identifier.
   *
   * @return UUID identifier
   */
  public String nextId() {
    return UUID.randomUUID().toString();
  }
}
