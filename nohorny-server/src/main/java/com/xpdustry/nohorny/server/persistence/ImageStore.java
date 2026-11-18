// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.server.database.SQLiteDatabase.Transaction;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/// The writes follow the transaction of the rows referencing the files: a stored file is removed again if it rolls
/// back, a deleted one only goes once it committed. The deletions run before the next write transaction begins, see
/// [SQLiteDatabase#withTransaction], so no other transaction can reference a file that is about to be deleted.
@Component
public final class ImageStore {

    private static final Logger log = LoggerFactory.getLogger(ImageStore.class);
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private final Path directory;

    public ImageStore(final StorageProperties properties) throws IOException {
        this.directory = Files.createDirectories(properties.images().toAbsolutePath());
    }

    public static String hash(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /// @return the [#hash] of the bytes
    public String store(final Transaction transaction, final byte[] bytes) {
        final var hash = hash(bytes);
        final var target = this.pathOf(hash);
        if (Files.exists(target)) {
            return hash;
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
        transaction.afterRollback(() -> remove(target));
        return hash;
    }

    public Optional<Resource> find(final String hash) {
        final var path = this.pathOf(hash);
        return Files.isRegularFile(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
    }

    public void delete(final Transaction transaction, final String hash) {
        final var path = this.pathOf(hash);
        transaction.afterCommit(() -> remove(path));
    }

    private Path pathOf(final String hash) {
        // The hashes come from the database, still, never let a name escape the directory
        if (!HASH.matcher(hash).matches()) {
            throw new IllegalArgumentException("Invalid image hash " + hash);
        }
        return this.directory.resolve(hash.substring(0, 2)).resolve(hash);
    }

    private static void remove(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (final IOException exception) {
            log.warn("Failed to delete the image file {}", path, exception);
        }
    }
}
