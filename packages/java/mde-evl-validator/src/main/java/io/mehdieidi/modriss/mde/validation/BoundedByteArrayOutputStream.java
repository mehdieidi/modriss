package io.mehdieidi.modriss.mde.validation;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Captures validator output up to a fixed byte budget while remembering whether additional output
 * was discarded.
 */
final class BoundedByteArrayOutputStream extends OutputStream {

  /** Accumulates the retained output bytes. */
  private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();

  /** Maximum number of bytes retained in {@link #delegate}. */
  private final int maxBytes;

  /** Indicates that at least one write exceeded the configured byte budget. */
  private boolean truncated;

  /**
   * Creates a bounded output stream.
   *
   * @param maxBytes requested byte limit; values below one are coerced to one
   */
  BoundedByteArrayOutputStream(int maxBytes) {
    this.maxBytes = Math.max(1, maxBytes);
  }

  /**
   * Writes a single byte when capacity remains, otherwise marks the capture as truncated.
   *
   * @param b byte value supplied by {@link OutputStream}
   */
  @Override
  public void write(int b) {
    if (delegate.size() < maxBytes) {
      delegate.write(b);
    } else {
      truncated = true;
    }
  }

  /**
   * Writes as much of a byte range as the remaining capture budget allows.
   *
   * @param bytes source byte array
   * @param offset first byte to copy
   * @param length number of requested bytes
   */
  @Override
  public void write(byte[] bytes, int offset, int length) {
    if (bytes == null || length <= 0) {
      return;
    }
    int remaining = maxBytes - delegate.size();
    if (remaining > 0) {
      delegate.write(bytes, offset, Math.min(length, remaining));
    }
    if (length > remaining) {
      truncated = true;
    }
  }

  /**
   * Returns the captured bytes as UTF-8 text with a truncation marker when output was discarded.
   *
   * @return captured UTF-8 text
   */
  String asUtf8String() {
    String value = delegate.toString(StandardCharsets.UTF_8);
    return truncated ? value + System.lineSeparator() + "[output truncated]" : value;
  }
}
