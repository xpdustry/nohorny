// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.config;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;
import org.github.gestalt.config.decoder.DecoderContext;
import org.github.gestalt.config.decoder.LeafDecoder;
import org.github.gestalt.config.decoder.Priority;
import org.github.gestalt.config.entity.ValidationError;
import org.github.gestalt.config.node.ConfigNode;
import org.github.gestalt.config.reflect.TypeCapture;
import org.github.gestalt.config.tag.Tags;
import org.github.gestalt.config.utils.GResultOf;
import org.github.gestalt.config.utils.StringUtils;

public final class FriendlyDurationDecoder extends LeafDecoder<Duration> {

    private static final Pattern FRIENDLY_PATTERN = Pattern.compile("^(\\d+)(ms|s|m|h|d)$", Pattern.CASE_INSENSITIVE);

    @Override
    public Priority priority() {
        return Priority.HIGH;
    }

    @Override
    public String name() {
        return "FriendlyDurationDecoder";
    }

    @Override
    public boolean canDecode(final String path, final Tags tags, final ConfigNode node, final TypeCapture<?> type) {
        return Duration.class.isAssignableFrom(type.getRawType());
    }

    @Override
    protected GResultOf<Duration> leafDecode(final String path, final ConfigNode node, final DecoderContext context) {
        final var value = node.getValue().orElse("");

        try {
            if (StringUtils.isInteger(value)) {
                return GResultOf.result(Duration.ofMillis(Long.parseLong(value)));
            }

            final var matcher = FRIENDLY_PATTERN.matcher(value.toLowerCase(Locale.ROOT));
            if (matcher.matches()) {
                final long amount = Long.parseLong(matcher.group(1));
                return GResultOf.result(
                        switch (matcher.group(2)) {
                            case "ms" -> Duration.ofMillis(amount);
                            case "s" -> Duration.ofSeconds(amount);
                            case "m" -> Duration.ofMinutes(amount);
                            case "h" -> Duration.ofHours(amount);
                            case "d" -> Duration.ofDays(amount);
                            default -> throw new IllegalStateException("Unexpected duration unit");
                        });
            }

            return GResultOf.result(Duration.parse(value));
        } catch (final Exception e) {
            return GResultOf.errors(
                    new ValidationError.ErrorDecodingException(path, node, this.name(), e.getMessage(), context));
        }
    }
}
