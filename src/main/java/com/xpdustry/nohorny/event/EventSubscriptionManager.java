// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.event;

import arc.Events;
import arc.func.Cons;
import com.xpdustry.nohorny.NoHornyPlugin;
import java.util.ArrayList;
import java.util.List;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.world.blocks.ConstructBlock;
import org.slf4j.LoggerFactory;

import static com.xpdustry.nohorny.event.BuildingUtils.anchorTileX;
import static com.xpdustry.nohorny.event.BuildingUtils.anchorTileY;

public final class EventSubscriptionManager {

    private final List<Runnable> subscriptions = new ArrayList<>();

    public <T> void subscribe(final Class<T> type, final EventListener<T> listener) {
        final Cons<T> cons = event -> {
            try {
                listener.onEvent(event);
            } catch (final Throwable e) {
                LoggerFactory.getLogger(NoHornyPlugin.class)
                        .error("An error occurred while handling event {}", type, e);
            }
        };
        Events.on(type, cons);
        this.subscriptions.add(() -> Events.remove(type, cons));
    }

    public <B extends Building> void subscribe(final Class<B> type, final BuildingLifecycleEventListener<B> listener) {
        this.subscribe(EventType.BlockBuildEndEvent.class, event -> {
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
                    listener.onCreate(casted, event.unit != null ? event.unit.getPlayer() : null);
                }
            }
        });

        this.subscribe(EventType.BlockDestroyEvent.class, event -> {
            if (type.isInstance(event.tile.build)) {
                listener.onRemove(
                        anchorTileX(event.tile.build), anchorTileY(event.tile.build), event.tile.build.block.size);
            }
        });

        this.subscribe(EventType.BuildingBulletDestroyEvent.class, event -> {
            if (type.isInstance(event.build)) {
                listener.onRemove(anchorTileX(event.build), anchorTileY(event.build), event.build.block.size);
            }
        });

        this.subscribe(EventType.BuildTeamChangeEvent.class, event -> {
            if (type.isInstance(event.build)) {
                final var casted = type.cast(event.build);
                listener.onRemove(anchorTileX(casted), anchorTileY(casted), casted.block.size);
                listener.onCreate(casted, null);
            }
        });

        this.subscribe(EventType.ConfigEvent.class, event -> {
            if (type.isInstance(event.tile)) {
                final var casted = type.cast(event.tile);
                listener.onRemove(anchorTileX(casted), anchorTileY(casted), casted.block.size);
                listener.onCreate(casted, event.player);
            }
        });

        this.subscribe(EventType.StateChangeEvent.class, event -> {
            if (event.from == GameState.State.menu
                    && (event.to == GameState.State.playing || event.to == GameState.State.paused)) {
                listener.onRemoveAll();
            }
        });
    }

    public void clear() {
        this.subscriptions.forEach(Runnable::run);
        this.subscriptions.clear();
    }
}
