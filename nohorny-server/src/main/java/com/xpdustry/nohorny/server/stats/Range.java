// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/// A window of whole slots ending with the current one.
public enum Range {
    H24("24h", Slot.HOUR, 24),
    D7("7d", Slot.DAY, 7),
    D30("30d", Slot.DAY, 30),
    D90("90d", Slot.DAY, 90);

    private final String key;
    private final Slot slot;
    private final int slots;

    Range(final String key, final Slot slot, final int slots) {
        this.key = key;
        this.slot = slot;
        this.slots = slots;
    }

    @JsonValue
    public String key() {
        return this.key;
    }

    public Slot slot() {
        return this.slot;
    }

    public int slots() {
        return this.slots;
    }

    /// @return the start of the oldest slot
    public Instant first() {
        final var unit = this.slot.unit;
        return Instant.now().truncatedTo(unit).minus(this.slots - 1, unit);
    }

    public static Optional<Range> fromKey(final String key) {
        for (final var range : values()) {
            if (range.key.equals(key)) {
                return Optional.of(range);
            }
        }
        return Optional.empty();
    }

    public enum Slot {
        HOUR("hour", ChronoUnit.HOURS),
        DAY("day", ChronoUnit.DAYS);

        private final String key;
        private final ChronoUnit unit;

        Slot(final String key, final ChronoUnit unit) {
            this.key = key;
            this.unit = unit;
        }

        @JsonValue
        public String key() {
            return this.key;
        }

        public ChronoUnit unit() {
            return this.unit;
        }

        /// @return the identifier of the slot starting at the instant in the statistics, the hours since the epoch or
        ///     the ISO date
        public String idOf(final Instant start) {
            return switch (this) {
                case HOUR ->
                    Long.toString(start.getEpochSecond()
                            / ChronoUnit.HOURS.getDuration().toSeconds());
                case DAY -> LocalDate.ofInstant(start, ZoneOffset.UTC).toString();
            };
        }
    }
}
