// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

/// The number of requests of a [RatingBucket].
public interface BucketCount {

    RatingBucket getBucket();

    long getTotal();
}
