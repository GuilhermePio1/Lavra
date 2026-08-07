package dev.lavra.shared.blob;

/**
 * What the API can know about an uploaded blob without reading a byte of it.
 *
 * @param blobPath    container-relative path
 * @param sizeBytes   size as stored
 * @param contentType type the client declared when uploading; may be null if it
 *                    declared none
 */
public record BlobMetadata(String blobPath, long sizeBytes, String contentType) {
}
