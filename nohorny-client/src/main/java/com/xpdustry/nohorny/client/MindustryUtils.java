// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Events;
import com.xpdustry.nohorny.common.MindustryAuthor;
import java.util.function.Function;
import java.util.function.Supplier;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.world.blocks.ConstructBlock;
import org.jspecify.annotations.Nullable;

import static com.xpdustry.nohorny.client.BuildingUtils.anchorTileX;
import static com.xpdustry.nohorny.client.BuildingUtils.anchorTileY;

final class MindustryUtils {

    private static final MiniLogger log = MiniLogger.forClass(MindustryUtils.class);

    public static <T> void onEvent(final Class<T> type, final EventListener<T> listener) {
        Events.on(type, event -> {
            try {
                listener.onEvent(event);
            } catch (final Throwable e) {
                log.error("An error occurred while handling event {}", type, e);
            }
        });
    }

    public static <T extends Enum<T>> void onEvent(final T type, final EventListener<T> listener) {
        Events.run(type, () -> {
            try {
                listener.onEvent(type);
            } catch (final Throwable e) {
                log.error("An error occurred while handling event {}", type, e);
            }
        });
    }

    public static <B extends Building> void onEvent(
            final Class<B> type, final BuildingLifecycleEventListener<B> listener) {
        MindustryUtils.onEvent(EventType.BlockBuildEndEvent.class, event -> {
            if (event.tile.build instanceof ConstructBlock.ConstructBuild constructing) {
                if (constructing.prevBuild != null) {
                    for (final var building : constructing.prevBuild) {
                        if (type.isInstance(building)) {
                            listener.onRemove(anchorTileX(building), anchorTileY(building), building.block.size);
                        }
                    }
                }
            }
            final var building = event.tile.build;
            if (type.isInstance(building)) {
                final var casted = type.cast(building);
                if (event.breaking) {
                    listener.onRemove(anchorTileX(casted), anchorTileY(casted), casted.block.size);
                } else {
                    listener.onCreate(casted, asAuthor(event.unit == null ? null : event.unit.getPlayer()));
                }
            }
        });

        MindustryUtils.onEvent(EventType.BlockDestroyEvent.class, event -> {
            if (type.isInstance(event.tile.build)) {
                listener.onRemove(
                        anchorTileX(event.tile.build), anchorTileY(event.tile.build), event.tile.build.block.size);
            }
        });

        MindustryUtils.onEvent(EventType.BuildingBulletDestroyEvent.class, event -> {
            if (type.isInstance(event.build)) {
                listener.onRemove(anchorTileX(event.build), anchorTileY(event.build), event.build.block.size);
            }
        });

        MindustryUtils.onEvent(EventType.BuildTeamChangeEvent.class, event -> {
            if (type.isInstance(event.build)) {
                final var casted = type.cast(event.build);
                listener.onRemove(anchorTileX(casted), anchorTileY(casted), casted.block.size);
                listener.onCreate(casted, null);
            }
        });

        MindustryUtils.onEvent(EventType.ConfigEvent.class, event -> {
            if (type.isInstance(event.tile)) {
                final var casted = type.cast(event.tile);
                listener.onRemove(anchorTileX(casted), anchorTileY(casted), casted.block.size);
                listener.onCreate(casted, asAuthor(event.player));
            }
        });

        MindustryUtils.onEvent(EventType.StateChangeEvent.class, event -> {
            if (event.from == GameState.State.menu
                    && (event.to == GameState.State.playing || event.to == GameState.State.paused)) {
                listener.onRemoveAll();
            }
        });
    }

    private static @Nullable MindustryAuthor asAuthor(final @Nullable Player player) {
        return player == null ? null : new MindustryAuthor(player.uuid(), player.ip());
    }

    private MindustryUtils() {}

    public static <T> Supplier<T> registerSafeSettingEntry(
            final String name, final String desc, final T def, final Function<String, T> parser) {
        return registerSafeSettingEntry(name, desc, def, parser, () -> {});
    }

    public static <T> Supplier<T> registerSafeSettingEntry(
            final String name,
            final String desc,
            final T def,
            final Function<String, T> parser,
            final Runnable onChange) {
        final var entry = new Administration.Config(name, desc, def.toString(), onChange) {
            @Override
            public void set(final Object value) {
                if (value instanceof String string) {
                    try {
                        final var _ = parser.apply(string);
                        super.set(string);
                    } catch (final Exception e) {
                        log.error("The value '{}' for the '{}' config entry is not valid", string, this.name, e);
                    }
                } else {
                    log.error(
                            "The value '{}' for the '{}' config entry is not a string",
                            value,
                            this.name,
                            new IllegalArgumentException());
                }
            }
        };
        return () -> {
            if (!entry.isString()) {
                return def;
            }
            try {
                return parser.apply(entry.string());
            } catch (final Exception _) {
                return def;
            }
        };
    }
}
