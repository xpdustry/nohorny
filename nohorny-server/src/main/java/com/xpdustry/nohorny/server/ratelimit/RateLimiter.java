// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.TokensInheritanceStrategy;
import java.time.Duration;
import org.springframework.stereotype.Component;

/// Token buckets refilled continuously over a minute, kept in memory.
///
/// A bucket idle for a minute is full again,
/// so the cache forgets it without changing the outcome of the next request.
@Component
public final class RateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    // Bounds the memory when many addresses show up, at worst a client gets a fresh bucket early
    private static final long MAX_BUCKETS = 100_000;

    private final Cache<Key, Limited> buckets = Caffeine.newBuilder()
            .expireAfterAccess(WINDOW)
            .maximumSize(MAX_BUCKETS)
            .build();

    /// Takes a token from the bucket of the key, created full on first use.
    ///
    /// When the limit of a bucket changes,
    /// a raise grants the extra tokens at once and a cut caps the remaining ones.
    ///
    /// @param scope what is limited, such as `classify-user`
    /// @param subject who is limited, such as a client address
    /// @param perMinute the capacity and refill rate of the bucket
    /// @return [Duration#ZERO] if a token was taken, otherwise how long to wait for the next one
    public Duration tryAcquire(final String scope, final String subject, final int perMinute) {
        final var limited = this.buckets.get(new Key(scope, subject), ignored -> new Limited(perMinute));
        return Duration.ofNanos(limited.tryAcquire(perMinute));
    }

    private static Bandwidth bandwidth(final int perMinute) {
        return Bandwidth.builder()
                .capacity(perMinute)
                .refillGreedy(perMinute, WINDOW)
                .build();
    }

    private record Key(String scope, String subject) {}

    private static final class Limited {

        private final Bucket bucket;
        private int perMinute;

        Limited(final int perMinute) {
            this.bucket = Bucket.builder().addLimit(bandwidth(perMinute)).build();
            this.perMinute = perMinute;
        }

        /// @return `0` if a token was taken, otherwise the nanoseconds to wait for the next one
        synchronized long tryAcquire(final int perMinute) {
            if (this.perMinute != perMinute) {
                this.bucket.replaceConfiguration(
                        BucketConfiguration.builder()
                                .addLimit(bandwidth(perMinute))
                                .build(),
                        TokensInheritanceStrategy.ADDITIVE);
                this.perMinute = perMinute;
            }
            return this.bucket.tryConsumeAndReturnRemaining(1).getNanosToWaitForRefill();
        }
    }
}
