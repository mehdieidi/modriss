package io.mehdieidi.varka.backend.upload;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Storage port for uploaded files. Implementations can target local disk, S3, or MinIO. */
public interface UploadStorage {

  /**
   * Stores one uploaded object and its metadata.
   *
   * @param scope upload ownership scope
   * @param originalFileName original browser-supplied file name
   * @param contentType submitted media type
   * @param content validated bytes
   * @return stored metadata
   * @throws IOException when persistence fails
   */
  UploadedFileRecord store(
      UploadScope scope, String originalFileName, String contentType, byte[] content)
      throws IOException;

  /**
   * Finds metadata by ID inside the supplied scope.
   *
   * @param scope upload ownership scope
   * @param id upload ID
   * @return metadata when present
   * @throws IOException when metadata cannot be read
   */
  Optional<UploadedFileRecord> find(UploadScope scope, String id) throws IOException;

  /**
   * Lists recent uploads inside the supplied ownership scope.
   *
   * @param scope upload ownership scope
   * @param limit maximum number of records to return
   * @return newest matching uploads first
   * @throws IOException when metadata cannot be listed
   */
  List<UploadedFileRecord> listRecent(UploadScope scope, int limit) throws IOException;

  /**
   * Reads stored content.
   *
   * @param record upload metadata
   * @return content bytes
   * @throws IOException when content cannot be read
   */
  byte[] read(UploadedFileRecord record) throws IOException;
}
