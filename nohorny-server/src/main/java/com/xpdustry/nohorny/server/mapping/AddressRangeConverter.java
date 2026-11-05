// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.mapping;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/// Stores the normalized CIDR notation of the range.
@Converter
public final class AddressRangeConverter implements AttributeConverter<AddressRange, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(final @Nullable AddressRange attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public @Nullable AddressRange convertToEntityAttribute(final @Nullable String column) {
        return column == null ? null : AddressRange.parse(column);
    }
}
