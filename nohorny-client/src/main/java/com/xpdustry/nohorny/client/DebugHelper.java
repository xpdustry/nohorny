// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.graphics.Color;
import arc.math.geom.Point2;
import arc.struct.IntMap;
import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.MindustryDisplay;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.MindustryPixel;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.world.blocks.logic.LogicDisplay;

final class DebugHelper implements LifecycleListener {

    private static final MiniLogger log = MiniLogger.forClass(DebugHelper.class);

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

    private final IntMap<DebugTap> taps = new IntMap<>();
    private final NoHornyEventBus events;
    private final Path directory;
    private final DisplayTracker displays;
    private final List<BuildingImageTracker<?, ?>> trackers;

    public DebugHelper(
            final NoHornyEventBus events,
            final Path directory,
            final DisplayTracker displays,
            final List<BuildingImageTracker<?, ?>> trackers) {
        this.events = events;
        this.directory = directory;
        this.displays = displays;
        this.trackers = List.copyOf(trackers);
        try {
            Files.createDirectories(directory);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create debug helper directory at " + directory, e);
        }
    }

    @Override
    public void onInit() {
        this.events.subscribe(EventType.PlayerLeave.class, event -> {
            this.taps.remove(event.player.id());
        });

        this.events.subscribe(EventType.TapEvent.class, event -> {
            final var enabled = NoHornySetting.DEBUG_TAP.get();
            if (enabled == null || !enabled || !event.player.admin()) {
                return;
            }
            final var tap = this.taps.get(event.player.id, DebugTap::new);
            if (System.currentTimeMillis() - tap.lastTapTimestamp > 1000L || tap.lastTapPos != event.tile.pos()) {
                tap.lastTapPos = event.tile.pos();
                tap.lastTapTimestamp = System.currentTimeMillis();
                return;
            }

            final var x = Point2.x(tap.lastTapPos);
            final var y = Point2.y(tap.lastTapPos);
            final var player = event.player;

            final var building = Vars.world.build(x, y);
            if (building instanceof LogicDisplay.LogicDisplayBuild) {
                this.groupDebugSnapshotAt(player, this.displays.displays, x, y);
            } else {
                for (final var tracker : this.trackers) {
                    if (tracker.buildingType.isInstance(building)) {
                        this.groupDebugSnapshotAt(player, tracker.index, x, y);
                        break;
                    }
                }
            }

            this.taps.remove(event.player.id);
        });
    }

    private <T extends MindustryImage> void groupDebugSnapshotAt(
            final Player player, final VirtualBuildingIndex<T> index, final int x, final int y) {
        // TODO We love lag spikes...
        final var grouper = index.selectGroupWithinRangeIncremental(x, y, 999, Integer.MAX_VALUE);
        grouper.progress();
        final var group = grouper.create();
        if (group == null) {
            player.sendMessage(NoHornyPlugin.MESSAGE_PREFIX + "[scarlet]No group at (" + x + ", " + y + ")");
            return;
        }
        this.highlight(player, group);
        final var prefix = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        .format(LocalDateTime.now(ZoneId.systemDefault()))
                        .replace(':', '-')
                + "_" + x + "_" + y;
        final var jpg = this.directory.resolve(prefix + ".jpg").toAbsolutePath();
        try (final var jpgStream = Files.newOutputStream(jpg)) {
            ReusableImageBytes.writeJpeg(MindustryImageRenderer.render(group), jpgStream);
        } catch (final IOException e) {
            player.sendMessage(NoHornyPlugin.MESSAGE_PREFIX + "[scarlet]Failed to create an image of the group at (" + x
                    + ", " + y + "), see console for stacktrace");
            log.error("Failed to process group at ({}, {}) for debugging", x, y, e);
            return;
        }
        player.sendMessage(NoHornyPlugin.MESSAGE_PREFIX + "Rendered group at (" + x + ", " + y + ") to " + jpg);
        log.info("{} rendered the group at ({}, {}) to {}", player.plainName(), x, y, jpg);
    }

    @SuppressWarnings("fallthrough")
    public <T extends MindustryImage> void highlight(final Player player, final VirtualBuilding.Group<T> group) {
        final var color = KELLY_COLORS[new Random().nextInt(KELLY_COLORS.length)];
        for (final var building : group.elements()) {
            switch (building.data()) {
                case MindustryDisplay display:
                    for (final var link : display.processors().keySet()) {
                        this.sendLabelIcon(
                                player,
                                color,
                                "P",
                                building.x() + GeometryUtils.x(link),
                                building.y() + GeometryUtils.y(link));
                    }
                case MindustryCanvas _, MindustryPixel _:
                    this.sendLabelIcon(player, color, "I", building.x(), building.y());
            }
        }
    }

    private void sendLabelIcon(final Player player, final Color color, final String icon, final int x, final int y) {
        Call.label(player.con(), "[#" + color + "]" + icon, 8F, x * Vars.tilesize, y * Vars.tilesize);
    }

    private static final class DebugTap {
        private int lastTapPos = -1;
        private long lastTapTimestamp = -1;
    }
}
