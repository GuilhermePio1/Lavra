package dev.lavra.shared.blob;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The {@link BlobStorage} the slice tests run against: it hands out tickets and
 * remembers what is supposedly stored, without any storage.
 *
 * <p>Uploading is the one step of the flow the API never performs — the browser
 * does it — so a test cannot reproduce it by calling anything. {@link #store}
 * stands in for the browser having finished, and is what lets the confirmation
 * path be exercised in both directions: the blob that matches the declaration
 * and the blob that does not.
 *
 * <p>Real storage is covered by {@code AzureBlobStorageIT} against Azurite; what
 * these tests need is the flow around it.
 */
public class RecordingBlobStorage implements BlobStorage {

    /** A ticket as it was handed out, for asserting scope and lifetime. */
    public record Issued(String blobPath, Duration ttl) {
    }

    private final Map<String, BlobMetadata> blobs = new ConcurrentHashMap<>();
    private final List<Issued> issued = new CopyOnWriteArrayList<>();
    private final Clock clock;

    public RecordingBlobStorage(Clock clock) {
        this.clock = clock;
    }

    @Override
    public WriteTicket issueWriteTicket(String blobPath, Duration ttl) {
        issued.add(new Issued(blobPath, ttl));
        return new WriteTicket(
                URI.create("https://storage.test/episodes/" + blobPath + "?sig=test"),
                blobPath,
                clock.instant().plus(ttl));
    }

    @Override
    public Optional<BlobMetadata> describe(String blobPath) {
        return Optional.ofNullable(blobs.get(blobPath));
    }

    @Override
    public void delete(String blobPath) {
        blobs.remove(blobPath);
    }

    /** Pretends the browser finished writing {@code blobPath}. */
    public void store(String blobPath, long sizeBytes, String contentType) {
        blobs.put(blobPath, new BlobMetadata(blobPath, sizeBytes, contentType));
    }

    public List<Issued> issued() {
        return List.copyOf(issued);
    }

    public void clear() {
        blobs.clear();
        issued.clear();
    }
}
