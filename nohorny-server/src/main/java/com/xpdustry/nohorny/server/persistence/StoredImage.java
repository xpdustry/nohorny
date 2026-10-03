// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.common.ImmutableByteArray;
import org.jspecify.annotations.Nullable;

/// The image of a [ClassificationRequest].
///
/// @param bytes the image as received, `null` unless the state is [ImageState#STORED]
public record StoredImage(
        ImageState state,
        @Nullable String mediaType,
        @Nullable ImmutableByteArray bytes) {

    /// The constructor of the JPQL projection.
    public StoredImage(final ImageState state, final @Nullable String mediaType, final byte @Nullable [] bytes) {
        this(state, mediaType, bytes == null ? null : ImmutableByteArray.wrap(bytes));
    }
}
