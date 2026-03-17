// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.config;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import org.github.gestalt.config.builder.GestaltBuilder;
import org.github.gestalt.config.decoder.BooleanDecoder;
import org.github.gestalt.config.decoder.DoubleDecoder;
import org.github.gestalt.config.decoder.EnumDecoder;
import org.github.gestalt.config.decoder.FloatDecoder;
import org.github.gestalt.config.decoder.IntegerDecoder;
import org.github.gestalt.config.decoder.ListDecoder;
import org.github.gestalt.config.decoder.OptionalDecoder;
import org.github.gestalt.config.decoder.RecordDecoder;
import org.github.gestalt.config.decoder.StringDecoder;
import org.github.gestalt.config.decoder.URIDecoder;
import org.github.gestalt.config.path.mapper.KebabCasePathMapper;
import org.github.gestalt.config.source.FileConfigSourceBuilder;
import org.jspecify.annotations.Nullable;

public final class GestaltConfigSupplier<C> implements Supplier<C> {

    private final Class<C> type;
    private final Path file;
    private volatile @Nullable C configuration = null;

    public GestaltConfigSupplier(final Class<C> type, final Path file) {
        this.type = type;
        this.file = file;
    }

    @Override
    public C get() {
        return Objects.requireNonNull(this.configuration, "configuration hasn't been loaded yet");
    }

    public void reload() throws Exception {
        final var builder = new GestaltBuilder()
                .addSource(FileConfigSourceBuilder.builder().setPath(this.file).build())
                .setTreatMissingDiscretionaryValuesAsErrors(false)
                .addPathMapper(new KebabCasePathMapper())
                .addConfigLoader(new GsonJsonLoader())
                .addDecoder(new RecordDecoder())
                .addDecoder(new URIDecoder())
                .addDecoder(new BooleanDecoder())
                .addDecoder(new DoubleDecoder())
                .addDecoder(new FriendlyDurationDecoder())
                .addDecoder(new EnumDecoder<>())
                .addDecoder(new FloatDecoder())
                .addDecoder(new IntegerDecoder())
                .addDecoder(new OptionalDecoder())
                .addDecoder(new StringDecoder())
                .addDecoder(new SealedConfigDecoder())
                .addDecoder(new ListDecoder())
                .addDecoder(new InetAddressDecoder());
        final var gestalt = builder.build();
        gestalt.loadConfigs();
        this.configuration = gestalt.getConfig("", this.type);
    }
}
