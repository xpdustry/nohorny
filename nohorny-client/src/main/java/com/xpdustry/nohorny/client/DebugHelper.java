// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.graphics.Color;
import arc.math.geom.Point2;
import arc.struct.IntMap;
import arc.util.Interval;
import arc.util.Time;
import com.xpdustry.nohorny.common.geometry.VirtualBuilding;
import com.xpdustry.nohorny.common.image.ImageRenderer;
import com.xpdustry.nohorny.common.image.MindustryCanvas;
import com.xpdustry.nohorny.common.image.MindustryDisplay;
import com.xpdustry.nohorny.common.image.MindustryImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import javax.imageio.ImageIO;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DebugHelper implements LifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugHelper.class);

    // https://stackoverflow.com/a/4382138
    private static final Color[] KELLY_COLORS = {
        new Color(0xFFB300FF), // Vivid Yellow
        new Color(0x803E75FF), // Strong Purple
        new Color(0xFF6800FF), // Vivid Orange
        new Color(0xA6BDD7FF), // Very Light Blue
        new Color(0xC10020FF), // Vivid Red
        new Color(0xCEA262FF), // Grayish Yellow
        new Color(0x817066FF), // Medium Gray
        new Color(0x007D34FF), // Vivid Green
        new Color(0xF6768EFF), // Strong Purplish Pink
        new Color(0x00538AFF), // Strong Blue
        new Color(0xFF7A5CFF), // Strong Yellowish Pink
        new Color(0x53377AFF), // Strong Violet
        new Color(0xFF8E00FF), // Vivid Orange Yellow
        new Color(0xB32851FF), // Strong Purplish Red
        new Color(0xF4C800FF), // Vivid Greenish Yellow
        new Color(0x7F180DFF), // Strong Reddish Brown
        new Color(0x93AA00FF), // Vivid Yellowish Green
        new Color(0x593315FF), // Deep Yellowish Brown
        new Color(0xF13A13FF), // Vivid Reddish Orange
        new Color(0x232C16FF), // Dark Olive Green
    };

    private final IntMap<DebugSession> debugging = new IntMap<>();
    private final Interval interval = new Interval();
    private final Path directory;
    private final CanvasTracker canvases;
    private final DisplayTracker displays;

    public DebugHelper(final Path directory, final CanvasTracker canvases, final DisplayTracker displays) {
        this.directory = directory;
        this.canvases = canvases;
        this.displays = displays;
        try {
            Files.createDirectories(directory);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create debug helper directory at " + directory, e);
        }
    }

    @Override
    public void onInit() {
        final var handler = Vars.netServer.clientCommands;

        handler.<Player>register("nohorny-debug", "Toggle NoHorny debugging features", (_, player) -> {
            if (this.debugging.containsKey(player.id())) {
                this.debugging.remove(player.id());
                player.sendMessage("Removed");
            } else {
                this.debugging.put(player.id(), new DebugSession());
                player.sendMessage("Added");
            }
        });

        MindustryUtils.onEvent(EventType.PlayerLeave.class, event -> {
            this.debugging.remove(event.player.id());
        });

        // TODO
        //   Replace tap events with a command,
        //   and maybe use a world label + regular labels + ids such as [D|C]-(001) for the overlay
        MindustryUtils.onEvent(EventType.TapEvent.class, event -> {
            final var session = this.debugging.get(event.player.id);
            if (session == null) {
                return;
            }
            if (System.currentTimeMillis() - session.lastTapTimestamp > 1000L
                    || session.lastTapPos != event.tile.pos()) {
                session.lastTapPos = event.tile.pos();
                session.lastTapTimestamp = System.currentTimeMillis();
                return;
            }

            final var x = Point2.x(session.lastTapPos);
            final var y = Point2.y(session.lastTapPos);
            final var player = event.player;

            this.canvases.scheduler.execute(() -> {
                final var group = this.canvases.canvases.groupAt(x, y);
                if (group == null) {
                    player.sendMessage("No canvas group at (" + x + ", " + y + ")");
                    return;
                }
                final var file =
                        this.directory.resolve(UUID.randomUUID() + ".png").toAbsolutePath();
                try (final var stream = Files.newOutputStream(file)) {
                    ImageIO.write(ImageRenderer.render(group), "png", stream);
                } catch (final IOException e) {
                    player.sendMessage("Failed to create an image of canvas group at (" + x + ", " + y
                            + "), see console for stacktrace");
                    LOGGER.error("Failed to process canvas group at ({}, {}}) for debug rendering", x, y, e);
                    return;
                }
                player.sendMessage("Rendered canvas group at (" + x + ", " + y + ") to " + file);
            });

            this.displays.scheduler.execute(() -> {
                final var group = this.displays.getGroupAt(x, y);
                if (group == null) {
                    player.sendMessage("No display group at (" + x + ", " + y + ")");
                    return;
                }
                final var file =
                        this.directory.resolve(UUID.randomUUID() + ".png").toAbsolutePath();
                try (final var stream = Files.newOutputStream(file)) {
                    ImageIO.write(ImageRenderer.render(group), "png", stream);
                } catch (final IOException e) {
                    player.sendMessage("Failed to create an image of display group at (" + x + ", " + y
                            + "), see console for stacktrace");
                    LOGGER.error("Failed to process display group at ({}, {}}) for debug rendering", x, y, e);
                    return;
                }
                player.sendMessage("Rendered display group at (" + x + ", " + y + ") to " + file);
            });
        });

        MindustryUtils.onEvent(EventType.Trigger.update, _ -> {
            if (this.interval.get(Time.toSeconds * 2)) {
                this.canvases.scheduler.execute(() -> this.render(this.canvases.canvases.groups()));
                this.displays.scheduler.execute(
                        () -> this.render(this.displays.assembleDisplayIndex().groups()));
            }
        });
    }

    @SuppressWarnings("fallthrough")
    public <T extends MindustryImage> void render(final Collection<VirtualBuilding.Group<T>> groups) {
        MindustryUtils.runInMainThread(() -> {
            if (this.debugging.isEmpty()) {
                return;
            }
            int i = 0;
            for (final var group : groups) {
                final var color = KELLY_COLORS[i++ % KELLY_COLORS.length];
                for (final var building : group.elements()) {
                    switch (building.data()) {
                        case MindustryDisplay display:
                            for (final var link : display.processors().keySet()) {
                                this.forEachDebugging(color, "P", building.x() + link.x(), building.y() + link.y());
                            }
                        case MindustryCanvas _:
                            this.forEachDebugging(color, "I", building.x(), building.y());
                    }
                }
            }
        });
    }

    private void forEachDebugging(final Color color, final String icon, final int x, final int y) {
        for (final var debugger : this.debugging) {
            final var player = Groups.player.getByID(debugger.key);
            Call.label(player.con(), "[#" + color + "]" + icon, 2.1F, x * Vars.tilesize, y * Vars.tilesize);
        }
    }

    private static final class DebugSession {
        private int lastTapPos = -1;
        private long lastTapTimestamp = -1;
    }
}
