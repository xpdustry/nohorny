// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import com.xpdustry.nohorny.server.persistence.ClassificationRequest;
import com.xpdustry.nohorny.server.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.server.persistence.DailyNetworkStatRepository;
import com.xpdustry.nohorny.server.persistence.DailyStatRepository;
import com.xpdustry.nohorny.server.persistence.ImageState;
import com.xpdustry.nohorny.server.persistence.ImageStore;
import com.xpdustry.nohorny.server.persistence.RatingBucket;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Records and reads the classification requests, images included.
///
/// The rows decide which images are stored, the files follow them within the same transaction: a file is written
/// when its first request is recorded and deleted once no stored request references it anymore.
@Service
public class RequestService {

    private final ClassificationRequestRepository requests;
    private final DailyStatRepository dailyStats;
    private final DailyNetworkStatRepository networkStats;
    private final ImageStore images;

    public RequestService(
            final ClassificationRequestRepository requests,
            final DailyStatRepository dailyStats,
            final DailyNetworkStatRepository networkStats,
            final ImageStore images) {
        this.requests = requests;
        this.dailyStats = dailyStats;
        this.networkStats = networkStats;
        this.images = images;
    }

    /// Records a request with its steps and counts it in the all-time statistics, those of its network included.
    ///
    /// @param image the image to store, given if and only if the image state of the request is [ImageState#STORED]
    @Transactional
    public void record(final ClassificationRequest request, final byte @Nullable [] image) {
        final var hash = request.getImageHash();
        if (image != null && hash != null) {
            this.images.store(hash, image);
        } else if (image != null || hash != null) {
            throw new IllegalArgumentException("The image must be given if and only if it is stored");
        }
        this.requests.save(request);
        this.dailyStats.increment(request.getCreatedAt(), request.bucket());
        final var network = request.getNetwork();
        if (network != null) {
            this.networkStats.increment(request.getCreatedAt(), network);
        }
    }

    public Optional<ClassificationRequest> find(final String id) {
        return this.requests.findById(id);
    }

    /// @param bucket only return the requests of this bucket, or all if `null`
    /// @param before only return the requests with an identifier lower than this one, thus older
    /// @return the newest requests matching the filters
    public List<ClassificationRequest> findPage(
            final @Nullable RatingBucket bucket, final @Nullable String before, final int limit) {
        return this.requests.findPage(bucket, before, limit);
    }

    /// @return the image of the request, empty if the request does not exist
    public Optional<RequestImage> findImage(final String id) {
        return this.requests.findById(id).map(request -> {
            final var hash = request.getImageHash();
            final var content = request.getImageState() == ImageState.STORED && hash != null
                    ? this.images.find(hash).orElse(null)
                    : null;
            return new RequestImage(request.getImageMediaType(), content);
        });
    }

    /// Deletes the image and marks it as purged, whatever its state.
    ///
    /// @return the purged request, empty if it does not exist
    @Transactional
    public Optional<ClassificationRequest> purgeImage(final String id) {
        final var request = this.requests.findById(id);
        if (request.isEmpty()) {
            return Optional.empty();
        }
        this.requests.purgeImage(id);
        this.deleteImageUnlessShared(request.get());
        return this.find(id);
    }

    /// @return `false` if the request does not exist
    @Transactional
    public boolean delete(final String id) {
        final var request = this.requests.findById(id);
        if (request.isEmpty()) {
            return false;
        }
        this.requests.deleteById(id);
        this.deleteImageUnlessShared(request.get());
        return true;
    }

    /// @return the number of expired images
    @Transactional
    public int expireImagesBefore(final Instant before) {
        final var orphans = this.requests.findHashesStoredOnlyBefore(before);
        final var expired = this.requests.expireImagesBefore(before);
        orphans.forEach(this.images::delete);
        return expired;
    }

    /// Deletes the requests created before the given instant, their images included. The daily statistics are kept.
    ///
    /// @return the number of deleted requests
    @Transactional
    public int deleteCreatedBefore(final Instant before) {
        final var orphans = this.requests.findHashesStoredOnlyBefore(before);
        final var deleted = this.requests.deleteCreatedBefore(before);
        orphans.forEach(this.images::delete);
        return deleted;
    }

    /// Deletes the image file of a request that no longer stores it, unless another request still does.
    ///
    /// @param request the request as it was before its row was updated or deleted
    private void deleteImageUnlessShared(final ClassificationRequest request) {
        final var hash = request.getImageHash();
        if (request.getImageState() == ImageState.STORED
                && hash != null
                && this.requests.countStoredByHash(hash) == 0) {
            this.images.delete(hash);
        }
    }
}
