// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.ImageBinaryCodec;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.io.IOException;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractSmartHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.MimeType;

public final class MindustryImageConverter
        extends AbstractSmartHttpMessageConverter<VirtualBuilding.Group<? extends MindustryImage>> {

    private static final MimeType MEDIA_TYPE = MimeType.valueOf(ImageBinaryCodec.MEDIA_TYPE);

    @Override
    public boolean canRead(final ResolvableType type, final @Nullable MediaType mediaType) {
        return ResolvableType.forClass(VirtualBuilding.Group.class).isAssignableFrom(type)
                && ResolvableType.forClass(MindustryImage.class).isAssignableFrom(type.getGeneric(0))
                && mediaType != null
                && mediaType.isCompatibleWith(MindustryImageConverter.MEDIA_TYPE);
    }

    @Override
    public VirtualBuilding.Group<? extends MindustryImage> read(
            final ResolvableType type, final HttpInputMessage inputMessage, final @Nullable Map<String, Object> hints)
            throws IOException, HttpMessageNotReadableException {
        try (final var stream = inputMessage.getBody()) {
            return ImageBinaryCodec.decode(stream);
        }
    }

    @Override
    public boolean canWrite(
            final ResolvableType targetType, final Class<?> valueClass, final @Nullable MediaType mediaType) {
        return false;
    }

    @Override
    protected void writeInternal(
            final VirtualBuilding.Group<? extends MindustryImage> group,
            final ResolvableType type,
            final HttpOutputMessage outputMessage,
            final @Nullable Map<String, Object> hints)
            throws HttpMessageNotWritableException {
        throw new UnsupportedOperationException();
    }
}
