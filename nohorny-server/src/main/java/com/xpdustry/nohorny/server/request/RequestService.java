// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import com.xpdustry.nohorny.server.persistence.ClassificationRequest;
import com.xpdustry.nohorny.server.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.server.persistence.ClassificationRequestSummary;
import com.xpdustry.nohorny.server.persistence.ClassificationStep;
import com.xpdustry.nohorny.server.persistence.ClassificationStepRow;
import com.xpdustry.nohorny.server.persistence.DailyNetworkStatRepository;
import com.xpdustry.nohorny.server.persistence.DailyStatRepository;
import com.xpdustry.nohorny.server.persistence.RatingBucket;
import com.xpdustry.nohorny.server.persistence.StoredImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Records and reads the classification requests.
@Service
public class RequestService {

    private final ClassificationRequestRepository requests;
    private final DailyStatRepository dailyStats;
    private final DailyNetworkStatRepository networkStats;

    public RequestService(
            final ClassificationRequestRepository requests,
            final DailyStatRepository dailyStats,
            final DailyNetworkStatRepository networkStats) {
        this.requests = requests;
        this.dailyStats = dailyStats;
        this.networkStats = networkStats;
    }

    /// Records a request with its steps and counts it in the all-time statistics, those of its network included.
    @Transactional
    public void record(final ClassificationRequest request) {
        this.requests.save(request);
        this.dailyStats.increment(request.getCreatedAt(), request.bucket());
        final var network = request.getNetwork();
        if (network != null) {
            this.networkStats.increment(request.getCreatedAt(), network);
        }
    }

    public Optional<ClassificationRequestSummary> find(final String id) {
        return this.requests
                .findSummaryById(id)
                .map(request -> this.withSteps(List.of(request)).getFirst());
    }

    /// @param bucket only return the requests of this bucket, or all if `null`
    /// @param before only return the requests with an identifier lower than this one, thus older
    /// @return the newest requests matching the filters
    public List<ClassificationRequestSummary> findPage(
            final @Nullable RatingBucket bucket, final @Nullable String before, final int limit) {
        return this.withSteps(this.requests.findSummaries(bucket, before, limit));
    }

    public Optional<StoredImage> findImage(final String id) {
        return this.requests.findImageById(id);
    }

    /// Deletes the image bytes and marks the image as purged, whatever its state.
    ///
    /// @return the purged request, empty if it does not exist
    @Transactional
    public Optional<ClassificationRequestSummary> purgeImage(final String id) {
        return this.requests.purgeImage(id) == 0 ? Optional.empty() : this.find(id);
    }

    /// @return `false` if the request does not exist
    @Transactional
    public boolean delete(final String id) {
        return this.requests.deleteById(id) > 0;
    }

    /// @return the number of expired images
    @Transactional
    public int expireImagesBefore(final Instant before) {
        return this.requests.expireImagesBefore(before);
    }

    /// @return the number of deleted requests
    @Transactional
    public int deleteCreatedBefore(final Instant before) {
        return this.requests.deleteCreatedBefore(before);
    }

    private List<ClassificationRequestSummary> withSteps(final List<ClassificationRequestSummary> requests) {
        if (requests.isEmpty()) {
            return requests;
        }
        final var steps = new HashMap<String, List<ClassificationStep>>();
        final var ids = requests.stream().map(ClassificationRequestSummary::id).toList();
        for (final ClassificationStepRow row : this.requests.findSteps(ids)) {
            steps.computeIfAbsent(row.requestId(), ignored -> new ArrayList<>()).add(row.step());
        }
        return requests.stream()
                .map(request -> request.withSteps(steps.getOrDefault(request.id(), List.of())))
                .toList();
    }
}
