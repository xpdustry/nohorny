// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.server.ServerTest;
import com.xpdustry.nohorny.server.database.SQLiteDatabase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ImageStoreTest extends ServerTest {

    @Autowired
    private ImageStore images;

    @Autowired
    private SQLiteDatabase database;

    @Test
    void rollbackRemovesStoredImage() {
        final var bytes = uniqueBytes();
        assertThrows(
                Rollback.class,
                () -> this.database.withTransaction(transaction -> {
                    this.images.store(transaction, bytes);
                    throw new Rollback();
                }));
        assertFalse(this.exists(bytes));
    }

    @Test
    void rollbackKeepsImageStoredBefore() {
        final var bytes = uniqueBytes();
        this.database.withTransaction(transaction -> this.images.store(transaction, bytes));
        assertThrows(
                Rollback.class,
                () -> this.database.withTransaction(transaction -> {
                    this.images.store(transaction, bytes);
                    throw new Rollback();
                }));
        assertTrue(this.exists(bytes));
    }

    @Test
    void deletionWaitsForCommit() {
        final var bytes = uniqueBytes();
        final var hash = this.database.withTransaction(transaction -> this.images.store(transaction, bytes));
        this.database.withTransaction(transaction -> {
            this.images.delete(transaction, hash);
            assertTrue(this.exists(bytes));
            return Boolean.TRUE;
        });
        assertFalse(this.exists(bytes));
    }

    @Test
    void rollbackKeepsDeletedImage() {
        final var bytes = uniqueBytes();
        final var hash = this.database.withTransaction(transaction -> this.images.store(transaction, bytes));
        assertThrows(
                Rollback.class,
                () -> this.database.withTransaction(transaction -> {
                    this.images.delete(transaction, hash);
                    throw new Rollback();
                }));
        assertTrue(this.exists(bytes));
    }

    private boolean exists(final byte[] bytes) {
        return this.images.find(ImageStore.hash(bytes)).isPresent();
    }

    private static byte[] uniqueBytes() {
        return UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
    }

    private static final class Rollback extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
