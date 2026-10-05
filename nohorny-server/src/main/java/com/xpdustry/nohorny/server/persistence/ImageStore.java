// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/// The image files, named by the SHA-256 hash of their content.
///
/// Identical images share one file, `request.image_hash` references it. The file lives at `ab/abcdef...` under the
/// directory, the first two hex characters of the hash shard the files over 256 directories.
///
/// The database decides whether an image is stored, this store only holds the bytes. Call it from the transaction
/// that writes the rows: the write lock of SQLite then serializes the file operations with those of the other
/// transactions, so a file is never deleted while a request that references it is being recorded.
@Component
public final class ImageStore {

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private final Path directory;

    public ImageStore(final StorageProperties properties) throws IOException {
        this.directory = Files.createDirectories(properties.images().toAbsolutePath());
    }

    /// @return the lowercase hex SHA-256 hash of the bytes, the name of their file
    public static String hash(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /// Stores the bytes unless a file with the same hash exists.
    ///
    /// @param hash the [#hash] of the bytes
    public void store(final String hash, final byte[] bytes) {
        final var target = this.pathOf(hash);
        if (Files.exists(target)) {
            return;
        }
        try {
            final var shard = Files.createDirectories(target.getParent());
            // Written aside then renamed, a reader never sees a partial file and concurrent writers of the same
            //   image both end up with a complete one
            final var temporary = Files.createTempFile(shard, hash, ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (final IOException exception) {
            throw new UncheckedIOException("Failed to store image " + hash, exception);
        }
    }

    /// @return the file of the image, empty if it does not exist
    public Optional<Resource> find(final String hash) {
        final var path = this.pathOf(hash);
        return Files.isRegularFile(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
    }

    /// Deletes the file of the image, if it exists.
    public void delete(final String hash) {
        try {
            Files.deleteIfExists(this.pathOf(hash));
        } catch (final IOException exception) {
            throw new UncheckedIOException("Failed to delete image " + hash, exception);
        }
    }

    private Path pathOf(final String hash) {
        // The hashes come from the database, still, never let a name escape the directory
        if (!HASH.matcher(hash).matches()) {
            throw new IllegalArgumentException("Invalid image hash " + hash);
        }
        return this.directory.resolve(hash.substring(0, 2)).resolve(hash);
    }
}
