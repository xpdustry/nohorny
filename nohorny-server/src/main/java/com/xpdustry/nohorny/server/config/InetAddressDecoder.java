// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.config;

import java.net.InetAddress;
import org.github.gestalt.config.decoder.Decoder;
import org.github.gestalt.config.decoder.DecoderContext;
import org.github.gestalt.config.decoder.Priority;
import org.github.gestalt.config.entity.ValidationError;
import org.github.gestalt.config.entity.ValidationLevel;
import org.github.gestalt.config.node.ConfigNode;
import org.github.gestalt.config.node.LeafNode;
import org.github.gestalt.config.reflect.TypeCapture;
import org.github.gestalt.config.tag.Tags;
import org.github.gestalt.config.utils.GResultOf;

public final class InetAddressDecoder implements Decoder<InetAddress> {

    @Override
    public Priority priority() {
        return Priority.MEDIUM;
    }

    @Override
    public String name() {
        return "InetAddressDecoder";
    }

    @Override
    public boolean canDecode(final String path, final Tags tags, final ConfigNode node, final TypeCapture<?> type) {
        return type.getRawType() == InetAddress.class && node instanceof LeafNode;
    }

    @Override
    public GResultOf<InetAddress> decode(
            final String path,
            final Tags tags,
            final ConfigNode node,
            final TypeCapture<?> type,
            final DecoderContext context) {
        final var value = node.getValue();
        if (value.isEmpty()) {
            return GResultOf.errors(new InvalidInetAddressError(path, ""));
        }
        try {
            return GResultOf.result(InetAddress.ofLiteral(value.get()));
        } catch (final Exception e) {
            return GResultOf.errors(new InvalidInetAddressError(path, value.get()));
        }
    }

    private static final class InvalidInetAddressError extends ValidationError {

        private final String path;
        private final String value;

        InvalidInetAddressError(final String path, final String value) {
            super(ValidationLevel.ERROR);
            this.path = path;
            this.value = value;
        }

        @Override
        public String description() {
            return "Invalid InetAddress '" + this.value + "' for path: " + this.path;
        }
    }
}
