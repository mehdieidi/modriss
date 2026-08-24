package io.mehdieidi.varka.platform.storage.api;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import tools.jackson.databind.ObjectMapper;

/**
 * Application-owned persistence port for platform records and blobs.
 *
 * <p>The application layer intentionally depends on this port instead of a concrete storage
 * adapter. Implementations own their physical schema, serialization details, atomic write strategy,
 * and path-safety rules.
 */
public interface PlatformStore {

  /**
   * Returns the JSON mapper used for application JSON value construction.
   *
   * @return configured mapper
   */
  ObjectMapper objectMapper();

  /**
   * Executes a group of persistence operations as one transaction when the adapter supports it.
   *
   * <p>The default is suitable for in-memory and test adapters. Durable adapters should override
   * this method so related records are committed or rolled back together.
   *
   * @param operation grouped persistence work
   * @param <T> result type
   * @return operation result
   */
  default <T> T inTransaction(Supplier<T> operation) {
    return operation.get();
  }

  /**
   * Reads a stored JSON record when it exists.
   *
   * @param path logical storage key
   * @param type target value type
   * @param <T> target value type
   * @return optional decoded value
   */
  <T> Optional<T> read(Path path, Class<T> type);

  /**
   * Reads a stored JSON record or raises a not-found platform error.
   *
   * @param path logical storage key
   * @param type target value type
   * @param message not-found message
   * @param <T> target value type
   * @return decoded value
   */
  default <T> T require(Path path, Class<T> type, String message) {
    return read(path, type).orElseThrow(() -> new PlatformException(404, message));
  }

  /**
   * Writes a JSON record.
   *
   * @param path logical storage key
   * @param value value to persist
   */
  void write(Path path, Object value);

  /**
   * Writes bytes atomically where supported by the adapter.
   *
   * @param path logical storage key
   * @param bytes bytes to persist
   */
  void writeBytesAtomically(Path path, byte[] bytes);

  /**
   * Reads stored bytes when they exist.
   *
   * @param path logical storage key
   * @return optional byte payload
   */
  Optional<byte[]> readBytes(Path path);

  /**
   * Deletes one stored value when it exists.
   *
   * @param path logical storage key
   */
  void deleteIfExists(Path path);

  /**
   * Lists JSON records under a logical directory.
   *
   * @param directory logical directory key
   * @param type target value type
   * @param <T> target value type
   * @return decoded records
   */
  <T> List<T> list(Path directory, Class<T> type);

  /**
   * Deletes all values under a logical directory.
   *
   * @param directory logical directory key
   */
  void deleteTree(Path directory);
}
