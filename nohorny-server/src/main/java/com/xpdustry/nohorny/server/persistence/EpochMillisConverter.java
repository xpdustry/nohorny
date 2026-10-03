// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// Stores instants as epoch milliseconds in `INTEGER` columns.
@Converter
public final class EpochMillisConverter implements AttributeConverter<Instant, Long> {

    @Override
    public @Nullable Long convertToDatabaseColumn(final @Nullable Instant attribute) {
        return attribute == null ? null : attribute.toEpochMilli();
    }

    @Override
    public @Nullable Instant convertToEntityAttribute(final @Nullable Long column) {
        return column == null ? null : Instant.ofEpochMilli(column);
    }
}
