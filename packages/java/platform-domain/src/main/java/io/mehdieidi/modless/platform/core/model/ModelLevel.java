package io.mehdieidi.modless.platform.core.model;

import io.mehdieidi.modless.platform.core.PlatformException;
import java.util.Locale;

/** Supported modeling abstraction levels. */
public enum ModelLevel {
  /** Computation Independent Model. */
  CIM,
  /** Platform Independent Model. */
  PIM,
  /** Platform Specific Model. */
  PSM;

  /**
   * Parses a case-insensitive API level name.
   *
   * @param value API value supplied by a caller
   * @return matching model level
   * @throws PlatformException when the value is unsupported
   */
  public static ModelLevel fromApiName(String value) {
    try {
      return ModelLevel.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException ex) {
      throw new PlatformException(400, "Unsupported model level: " + value);
    }
  }

  /**
   * Returns the lowercase API representation of this level.
   *
   * @return API level name
   */
  public String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
