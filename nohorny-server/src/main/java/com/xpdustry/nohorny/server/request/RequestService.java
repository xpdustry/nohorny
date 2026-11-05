// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

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
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestService {

    private final ClassificationRequestRepository requests;
    private final DailyStatRepository dailyStats;
    private final ImageStore images;

    public RequestService(
            final ClassificationRequestRepository requests,
            final DailyStatRepository dailyStats,
            final ImageStore images) {
        this.requests = requests;
        this.dailyStats = dailyStats;
        this.images = images;
    }

    @Transactional
    public void record(final ClassificationRequest request, final byte[] image) {
        if (!this.images.store(image).equals(request.getImageHash())) {
            throw new IllegalArgumentException("The request does not reference the image given");
        }
        this.requests.save(request);
        this.dailyStats.increment(request);
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

    @Transactional
    public Optional<ClassificationRequest> deleteImage(final String id) {
        final var request = this.requests.findById(id);
        if (request.isEmpty()) {
            return Optional.empty();
        }
        final var hash = storedHash(request.get());
        this.requests.markImageDeleted(id);
        hash.ifPresent(this::deleteUnlessShared);
        return this.find(id);
    }

    @Transactional
    public boolean delete(final String id) {
        final var request = this.requests.findById(id);
        if (request.isEmpty()) {
            return false;
        }
        final var hash = storedHash(request.get());
        this.requests.deleteById(id);
        hash.ifPresent(this::deleteUnlessShared);
        return true;
    }

    @Transactional
    public int expireImagesBefore(final Instant before) {
        final var orphans = this.requests.findHashesStoredOnlyBefore(before);
        final var expired = this.requests.markImagesExpiredBefore(before);
        orphans.forEach(this.images::delete);
        return expired;
    }

    @Transactional
    public int deleteCreatedBefore(final Instant before) {
        final var orphans = this.requests.findHashesStoredOnlyBefore(before);
        final var deleted = this.requests.deleteCreatedBefore(before);
        orphans.forEach(this.images::delete);
        return deleted;
    }

    private void deleteUnlessShared(final String hash) {
        if (this.requests.countStoredByHash(hash) == 0) {
            this.images.delete(hash);
        }
    }

    private static Optional<String> storedHash(final ClassificationRequest request) {
        return request.getImageState() == ImageState.STORED
                ? Optional.ofNullable(request.getImageHash())
                : Optional.empty();
    }
}
