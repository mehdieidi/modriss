package io.mehdieidi.modless.platform.core;

/** Runtime exception carrying an HTTP-compatible status code for platform service failures. */
public class PlatformException extends RuntimeException {

  /** HTTP-compatible status code returned to API callers. */
  private final int status;

  /**
   * Creates a platform exception.
   *
   * @param status HTTP-compatible status code
   * @param message user-facing failure message
   */
  public PlatformException(int status, String message) {
    super(message);
    this.status = status;
  }

  /**
   * Returns the HTTP-compatible status code.
   *
   * @return status code
   */
  public int status() {
    return status;
  }
}
