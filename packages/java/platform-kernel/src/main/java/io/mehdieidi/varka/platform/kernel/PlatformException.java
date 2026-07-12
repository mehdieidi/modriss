package io.mehdieidi.varka.platform.kernel;

/** Runtime exception carrying an HTTP-compatible status code for platform service failures. */
public class PlatformException extends RuntimeException {

  /** HTTP-compatible status code returned to API callers. */
  private final int status;

  public PlatformException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
