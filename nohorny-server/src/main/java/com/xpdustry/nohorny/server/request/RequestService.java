// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import com.xpdustry.nohorny.server.database.SQLiteDatabase;
import com.xpdustry.nohorny.server.database.SQLiteDatabase.Transaction;
import com.xpdustry.nohorny.server.persistence.ClassificationRequest;
import com.xpdustry.nohorny.server.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.server.persistence.DailyStatRepository;
import com.xpdustry.nohorny.server.persistence.ImageState;
import com.xpdustry.nohorny.server.persistence.ImageStore;
import com.xpdustry.nohorny.server.persistence.RequestFilter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class RequestService {

    private final SQLiteDatabase database;
    private final ClassificationRequestRepository requests;
    private final DailyStatRepository dailyStats;
    private final ImageStore images;

    public RequestService(
            final SQLiteDatabase database,
            final ClassificationRequestRepository requests,
            final DailyStatRepository dailyStats,
            final ImageStore images) {
        this.database = database;
        this.requests = requests;
        this.dailyStats = dailyStats;
        this.images = images;
    }

    public void record(final ClassificationRequest request, final byte[] image) {
        this.database.withTransaction(transaction -> {
            if (!this.images.store(transaction, image).equals(request.imageHash())) {
                throw new IllegalArgumentException("The request does not reference the image given");
            }
            this.requests.insert(request);
            this.dailyStats.increment(request);
            return Boolean.TRUE;
        });
    }

    public Optional<ClassificationRequest> find(final String id) {
        return this.requests.findById(id);
    }

    public List<ClassificationRequest> findPage(
            final RequestFilter filter, final @Nullable String before, final int limit) {
        return this.requests.findPage(filter, before, limit);
    }

    public Optional<Resource> findImage(final ClassificationRequest request) {
        return storedHash(request).flatMap(this.images::find);
    }

    public Optional<ClassificationRequest> deleteImage(final String id) {
        return this.database.withTransaction(transaction -> {
            final var request = this.requests.findById(id);
            if (request.isEmpty()) {
                return Optional.empty();
            }
            final var hash = storedHash(request.get());
            this.requests.markImageDeleted(id);
            if (hash.isPresent()) {
                this.deleteUnlessShared(transaction, hash.get());
            }
            return this.find(id);
        });
    }

    public boolean delete(final String id) {
        return this.database.withTransaction(transaction -> {
            final var request = this.requests.findById(id);
            if (request.isEmpty()) {
                return false;
            }
            final var hash = storedHash(request.get());
            this.requests.deleteById(id);
            if (hash.isPresent()) {
                this.deleteUnlessShared(transaction, hash.get());
            }
            return true;
        });
    }

    public int expireImagesBefore(final Instant before) {
        return this.database.withTransaction(transaction -> {
            final var orphans = this.requests.findHashesStoredOnlyBefore(before);
            final var expired = this.requests.markImagesExpiredBefore(before);
            orphans.forEach(hash -> this.images.delete(transaction, hash));
            return expired;
        });
    }

    public int deleteCreatedBefore(final Instant before) {
        return this.database.withTransaction(transaction -> {
            final var orphans = this.requests.findHashesStoredOnlyBefore(before);
            final var deleted = this.requests.deleteCreatedBefore(before);
            orphans.forEach(hash -> this.images.delete(transaction, hash));
            return deleted;
        });
    }

    private void deleteUnlessShared(final Transaction transaction, final String hash) {
        if (this.requests.countStoredByHash(hash) == 0) {
            this.images.delete(transaction, hash);
        }
    }

    private static Optional<String> storedHash(final ClassificationRequest request) {
        return request.imageState() == ImageState.STORED ? Optional.ofNullable(request.imageHash()) : Optional.empty();
    }
}
