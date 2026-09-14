package io.mehdieidi.modriss.mde.etl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Generates globally unique identifiers for model elements created by ETL transformations. */
public final class ModelElementIdGenerator {

  private final Map<String, Integer> occurrencesByKey = new LinkedHashMap<>();

  /**
   * Generates the next model element identifier.
   *
   * @return UUID identifier
   */
  public String nextId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Generates a repeatable, collision-resistant identifier for a transformation-owned element.
   *
   * <p>The namespace identifies the transformation direction and the semantic key identifies the
   * source element and target role. Neither value is normalized: callers must deliberately supply
   * stable semantic identity instead of a display name.
   *
   * @param namespace transformation namespace
   * @param semanticKey stable source/role key
   * @return lowercase SHA-256 based identifier
   */
  public String deterministicId(String namespace, String semanticKey) {
    String canonicalNamespace = requireText(namespace, "namespace");
    String canonicalKey = requireText(semanticKey, "semanticKey");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash =
          digest.digest(
              (canonicalNamespace + "\u0000" + canonicalKey).getBytes(StandardCharsets.UTF_8));
      long mostSignificant = 0L;
      long leastSignificant = 0L;
      for (int index = 0; index < 8; index++) {
        mostSignificant = (mostSignificant << 8) | (hash[index] & 0xffL);
        leastSignificant = (leastSignificant << 8) | (hash[index + 8] & 0xffL);
      }
      mostSignificant = (mostSignificant & 0xffffffffffff0fffL) | 0x0000000000005000L;
      leastSignificant = (leastSignificant & 0x3fffffffffffffffL) | 0x8000000000000000L;
      return new UUID(mostSignificant, leastSignificant).toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime.", ex);
    }
  }

  /**
   * Generates a repeatable identifier for one occurrence of a possibly repeated semantic key.
   *
   * <p>Generator instances are scoped to one ETL execution, so an identical traversal produces
   * identical identifiers on the next execution while repeated helper objects remain distinct.
   *
   * @param namespace transformation namespace
   * @param semanticKey stable helper-object key
   * @return deterministic identifier for this key occurrence
   */
  public String deterministicOccurrenceId(String namespace, String semanticKey) {
    String canonicalNamespace = requireText(namespace, "namespace");
    String canonicalKey = requireText(semanticKey, "semanticKey");
    String occurrenceKey = canonicalNamespace + "\u0000" + canonicalKey;
    int occurrence = occurrencesByKey.merge(occurrenceKey, 1, Integer::sum);
    return deterministicId(canonicalNamespace, canonicalKey + "::" + occurrence);
  }

  private String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank.");
    }
    return value;
  }
}
