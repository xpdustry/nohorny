// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;

/// The image of a classification request, as served by the API.
///
/// @param mediaType the media type of the image, if it was stored at some point
/// @param content the image file, `null` when the image is not available: never stored, expired, purged or missing
public record RequestImage(
        @Nullable String mediaType, @Nullable Resource content) {}
