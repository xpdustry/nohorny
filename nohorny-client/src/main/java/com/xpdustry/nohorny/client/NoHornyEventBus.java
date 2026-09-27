// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Events;
import arc.func.Cons;
import com.xpdustry.nohorny.common.MindustryAuthor;
import java.util.ArrayList;
import java.util.List;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Player;
import mindustry.world.blocks.ConstructBlock;
import org.jspecify.annotations.Nullable;

// Turns the Mindustry building events into create/remove callbacks per building type
final class NoHornyEventBus implements AutoCloseable {

    private static final MiniLogger log = MiniLogger.forClass(NoHornyEventBus.class);

    private final List<ArcSubscription<?>> subscriptions = new ArrayList<>();
    private final List<BuildingDispatch<?>> buildingDispatches = new ArrayList<>();
    private int preChangePos = -1;
    private @Nullable Building preChangeBuild = null;

    NoHornyEventBus() {
        this.subscribe(EventType.BlockBuildEndEvent.class, event -> {
            if (event.tile.build instanceof ConstructBlock.ConstructBuild constructing
                    && constructing.prevBuild != null) {
                for (final var building : constructing.prevBuild) {
                    for (final var dispatch : this.buildingDispatches) {
                        dispatch.remove(building);
                    }
                }
            }
            final var author = event.breaking ? null : asAuthor(event.unit == null ? null : event.unit.getPlayer());
            for (final var dispatch : this.buildingDispatches) {
                if (event.breaking) {
                    dispatch.remove(event.tile.build);
                } else {
                    dispatch.create(event.tile.build, author, true);
                }
            }
        });

        this.subscribe(EventType.BlockDestroyEvent.class, event -> {
            for (final var dispatch : this.buildingDispatches) {
                dispatch.remove(event.tile.build);
            }
        });

        this.subscribe(EventType.BuildingBulletDestroyEvent.class, event -> {
            for (final var dispatch : this.buildingDispatches) {
                dispatch.remove(event.build);
            }
        });

        this.subscribe(EventType.BuildTeamChangeEvent.class, event -> {
            for (final var dispatch : this.buildingDispatches) {
                dispatch.remove(event.build);
                dispatch.create(event.build, null, false);
            }
        });

        this.subscribe(EventType.ConfigEvent.class, event -> {
            final var author = asAuthor(event.player);
            for (final var dispatch : this.buildingDispatches) {
                dispatch.remove(event.tile);
                dispatch.create(event.tile, author, true);
            }
        });

        this.subscribe(EventType.StateChangeEvent.class, event -> {
            if (event.from == GameState.State.menu
                    && (event.to == GameState.State.playing || event.to == GameState.State.paused)) {
                this.load();
            }
        });

        // For server side map modifications, e.g. AutoModerator#delete

        this.subscribe(EventType.TilePreChangeEvent.class, event -> {
            this.preChangePos = event.tile.pos();
            this.preChangeBuild = event.tile.build;
        });

        this.subscribe(EventType.TileChangeEvent.class, event -> {
            final var previous = this.preChangeBuild;
            final var matches = event.tile.pos() == this.preChangePos;
            this.preChangePos = -1;
            this.preChangeBuild = null;
            if (!matches) {
                // This should never happen...
                return;
            }
            for (final var dispatch : this.buildingDispatches) {
                dispatch.replace(previous, event.tile.build, event.tile.x, event.tile.y);
            }
        });
    }

    public <E> Subscription subscribe(final Class<E> event, final Subscriber<E> subscriber) {
        final var subscription = new ArcSubscription<>(event, subscriber);
        Events.on(event, subscription);
        this.subscriptions.add(subscription);
        return subscription;
    }

    public <B extends Building> Subscription subscribe(final Class<B> type, final BuildingSubscriber<B> subscriber) {
        final var dispatch = new BuildingDispatch<>(type, subscriber);
        this.buildingDispatches.add(dispatch);
        return () -> this.buildingDispatches.remove(dispatch);
    }

    public <E> void publish(final E event) {
        Events.fire(event);
    }

    @Override
    public void close() {
        for (final var subscription : this.subscriptions) {
            subscription.remove();
        }
        this.subscriptions.clear();
        this.buildingDispatches.clear();
    }

    private void load() {
        for (final var dispatch : this.buildingDispatches) {
            dispatch.removeAll();
        }
        for (final var tile : Vars.world.tiles) {
            // Multi-tile buildings are present on each of their tiles
            if (tile.build != null && tile.isCenter()) {
                for (final var dispatch : this.buildingDispatches) {
                    dispatch.create(tile.build, null, false);
                }
            }
        }
    }

    private static @Nullable MindustryAuthor asAuthor(final @Nullable Player player) {
        return player == null ? null : new MindustryAuthor(player.uuid(), player.ip());
    }

    private record BuildingDispatch<B extends Building>(Class<B> type, BuildingSubscriber<B> subscriber) {

        private void create(
                final @Nullable Building building, final @Nullable MindustryAuthor author, final boolean queue) {
            if (this.type.isInstance(building)) {
                try {
                    this.subscriber.onCreate(this.type.cast(building), author, queue);
                } catch (final Throwable e) {
                    this.error(e);
                }
            }
        }

        private void remove(final @Nullable Building building) {
            if (building != null && this.type.isInstance(building)) {
                try {
                    this.subscriber.onRemove(
                            MindustryUtils.anchorTileX(building),
                            MindustryUtils.anchorTileY(building),
                            building.block.size);
                } catch (final Throwable e) {
                    this.error(e);
                }
            }
        }

        private void replace(
                final @Nullable Building previous, final @Nullable Building current, final int x, final int y) {
            if (this.type.isInstance(current)) {
                this.create(current, null, false);
            } else if (this.type.isInstance(previous)) {
                try {
                    this.subscriber.onRemove(x, y, 1);
                } catch (final Throwable e) {
                    this.error(e);
                }
            }
        }

        private void removeAll() {
            try {
                this.subscriber.onRemoveAll();
            } catch (final Throwable e) {
                this.error(e);
            }
        }

        private void error(final Throwable e) {
            log.error("An error occurred while dispatching a building event to {}", this.type, e);
        }
    }

    @FunctionalInterface
    interface Subscriber<E> {

        void onEvent(final E event);
    }

    interface BuildingSubscriber<B extends Building> {

        void onCreate(final B building, final @Nullable MindustryAuthor author, final boolean queue);

        void onRemove(final int x, final int y, final int size);

        void onRemoveAll();
    }

    @FunctionalInterface
    interface Subscription {

        void unsubscribe();
    }

    // Registered as the arc listener itself, since Events.remove matches listeners with equals
    private final class ArcSubscription<E> implements Cons<E>, Subscription {

        private final Class<E> event;
        private final Subscriber<E> subscriber;

        private ArcSubscription(final Class<E> event, final Subscriber<E> subscriber) {
            this.event = event;
            this.subscriber = subscriber;
        }

        @Override
        public void unsubscribe() {
            this.remove();
            NoHornyEventBus.this.subscriptions.remove(this);
        }

        private void remove() {
            Events.remove(this.event, this);
        }

        @Override
        public void get(final E event) {
            try {
                this.subscriber.onEvent(event);
            } catch (final Throwable e) {
                log.error(
                        "An error occurred while handling a {} event.",
                        event.getClass().getSimpleName(),
                        e);
            }
        }
    }
}
