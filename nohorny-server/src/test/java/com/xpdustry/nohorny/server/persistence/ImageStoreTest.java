// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.server.ServerTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ImageStoreTest extends ServerTest {

    @Autowired
    private ImageStore images;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void rollbackRemovesStoredImage() {
        final var bytes = uniqueBytes();
        assertThrows(
                Rollback.class,
                () -> this.transactions.executeWithoutResult(status -> {
                    this.images.store(bytes);
                    throw new Rollback();
                }));
        assertFalse(this.exists(bytes));
    }

    @Test
    void rollbackKeepsImageStoredBefore() {
        final var bytes = uniqueBytes();
        this.transactions.executeWithoutResult(status -> this.images.store(bytes));
        assertThrows(
                Rollback.class,
                () -> this.transactions.executeWithoutResult(status -> {
                    this.images.store(bytes);
                    throw new Rollback();
                }));
        assertTrue(this.exists(bytes));
    }

    @Test
    void deletionWaitsForCommit() {
        final var bytes = uniqueBytes();
        final var hash = this.transactions.execute(status -> this.images.store(bytes));
        this.transactions.executeWithoutResult(status -> {
            this.images.delete(hash);
            assertTrue(this.exists(bytes));
        });
        assertFalse(this.exists(bytes));
    }

    @Test
    void rollbackKeepsDeletedImage() {
        final var bytes = uniqueBytes();
        final var hash = this.transactions.execute(status -> this.images.store(bytes));
        assertThrows(
                Rollback.class,
                () -> this.transactions.executeWithoutResult(status -> {
                    this.images.delete(hash);
                    throw new Rollback();
                }));
        assertTrue(this.exists(bytes));
    }

    @Test
    void writeOutsideTransactionFails() {
        assertThrows(IllegalStateException.class, () -> this.images.store(uniqueBytes()));
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
