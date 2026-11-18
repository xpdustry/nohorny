// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

/// @param slot an ISO-8601 date for days or the epoch hour for hours
public record SlotCount(String slot, RatingBucket bucket, long total) {}
