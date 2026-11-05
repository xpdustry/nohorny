// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/// The writes follow the transaction of the rows referencing the files: a stored file is removed again if it rolls
/// back, a deleted one only goes once it committed. [#store] and [#delete] must be called from a transaction.
@Component
public final class ImageStore {

    private static final Logger log = LoggerFactory.getLogger(ImageStore.class);
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private final Path directory;

    // Held from the first write of a transaction until it completes. The deletions run after the commit released the
    // write lock of SQLite, without it another transaction could reference a file that is about to be deleted.
    private final ReentrantLock lock = new ReentrantLock();

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
    public String store(final byte[] bytes) {
        final var transaction = this.transaction();
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
        transaction.stored.add(target);
        return hash;
    }

    public Optional<Resource> find(final String hash) {
        final var path = this.pathOf(hash);
        return Files.isRegularFile(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
    }

    public void delete(final String hash) {
        this.transaction().deleted.add(this.pathOf(hash));
    }

    private Path pathOf(final String hash) {
        // The hashes come from the database, still, never let a name escape the directory
        if (!HASH.matcher(hash).matches()) {
            throw new IllegalArgumentException("Invalid image hash " + hash);
        }
        return this.directory.resolve(hash.substring(0, 2)).resolve(hash);
    }

    private Transaction transaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The images can only be written from a transaction");
        }
        if (TransactionSynchronizationManager.getResource(this) instanceof Transaction existing) {
            return existing;
        }
        this.lock.lock();
        final var created = new Transaction();
        TransactionSynchronizationManager.bindResource(this, created);
        TransactionSynchronizationManager.registerSynchronization(created);
        return created;
    }

    private final class Transaction implements TransactionSynchronization {

        private final List<Path> stored = new ArrayList<>();
        private final List<Path> deleted = new ArrayList<>();

        @Override
        public void afterCommit() {
            this.deleted.forEach(Transaction::remove);
        }

        @Override
        public void afterCompletion(final int status) {
            try {
                if (status == STATUS_ROLLED_BACK) {
                    this.stored.forEach(Transaction::remove);
                }
            } finally {
                TransactionSynchronizationManager.unbindResource(ImageStore.this);
                ImageStore.this.lock.unlock();
            }
        }

        private static void remove(final Path path) {
            try {
                Files.deleteIfExists(path);
            } catch (final IOException exception) {
                log.warn("Failed to delete the image file {}", path, exception);
            }
        }
    }
}
