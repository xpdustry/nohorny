// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.security.SecureRandom;
import java.util.UUID;

/// Generates [RFC 9562](https://www.rfc-editor.org/rfc/rfc9562#name-uuid-version-7) version 7 identifiers.
///
/// The 48 most significant bits hold the unix timestamp in milliseconds, so identifiers sort chronologically,
/// even as lowercase strings. The 12 bits following the version are a counter seeded randomly every millisecond,
/// which keeps the identifiers monotonic within the same millisecond. The 62 remaining bits are random.
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long COUNTER_BITS = 12;
    private static final long COUNTER_MASK = (1L << COUNTER_BITS) - 1;

    // Timestamp shifted left by the counter bits, ORed with the counter
    private static long last = 0L;

    private UuidV7() {}

    public static UUID next() {
        final long sequence;
        synchronized (UuidV7.class) {
            // The upper half of the counter starts at 0, leaving room for 2048+ identifiers per millisecond
            final var seeded = (System.currentTimeMillis() << COUNTER_BITS) | RANDOM.nextInt(1 << (COUNTER_BITS - 1));
            // An overflowing counter borrows the next millisecond, which also absorbs clock regressions
            sequence = Math.max(seeded, last + 1);
            last = sequence;
        }
        final var msb = ((sequence >>> COUNTER_BITS) << 16) | 0x7000L | (sequence & COUNTER_MASK);
        final var lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
