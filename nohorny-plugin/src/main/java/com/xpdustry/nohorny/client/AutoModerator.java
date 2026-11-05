// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.struct.IntMap;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.MindustryDisplay;
import com.xpdustry.nohorny.common.MindustryPixel;
import com.xpdustry.nohorny.common.Rating;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.io.JsonIO;
import mindustry.type.ItemSeq;
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.blocks.logic.CanvasBlock;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;
import mindustry.world.blocks.power.LightBlock;
import org.jspecify.annotations.Nullable;

final class AutoModerator implements LifecycleListener {

    private static final MiniLogger log = MiniLogger.forClass(AutoModerator.class);

    private final NoHornyEventBus events;

    public AutoModerator(final NoHornyEventBus events) {
        this.events = events;
    }

    @Override
    public void onInit() {
        this.events.subscribe(ClassificationEvent.class, this::onClassificationEvent);
    }

    private void onClassificationEvent(final ClassificationEvent event) {
        final var policy = NoHornySetting.AUTO_MOD_POLICY.get();
        if (policy == null) {
            return;
        }
        switch (policy) {
            case BAN_NSFW -> {
                if (event.response().rating().isWorseOrEqualThan(Rating.NSFW)) {
                    this.delete(event);
                    if (event.author() != null) {
                        this.ban(
                                event.author(), event.group().x(), event.group().y());
                    }
                }
            }
            case DELETE_WARN -> {
                if (event.response().rating().isWorseOrEqualThan(Rating.WARN)) {
                    this.delete(event);
                }
            }
            case DELETE_NSFW -> {
                if (event.response().rating().isWorseOrEqualThan(Rating.NSFW)) {
                    this.delete(event);
                }
            }
            case DISABLED -> {}
        }
    }

    private void ban(final MindustryAuthor author, final int groupX, final int groupY) {
        log.info(
                "Banning ({}/{}) for NSFW buildings at ({}, {})",
                Vars.netServer.admins.getInfo(author.uuid()).plainLastName(),
                author.uuid(),
                groupX,
                groupY);
        Vars.netServer.admins.banPlayerIP(author.ip());
        Vars.netServer.admins.banPlayer(author.uuid());
        for (final var player : Groups.player) {
            if (player.uuid().equals(author.uuid()) || player.ip().equals(author.ip())) {
                player.kick("[scarlet]You have been banned for placing NSFW buildings.");
                Call.sendMessage(NoHornyPlugin.MESSAGE_PREFIX + player.plainName()
                        + " has been banned for placing NSFW buildings at "
                        + groupX + ", "
                        + groupY + ".");
            }
        }
    }

    private void delete(final ClassificationEvent event) {
        final var group = event.group();
        log.info(
                "Building group within ({}, {}) and ({}, {}) is unsafe, deleting",
                group.x(),
                group.y(),
                group.x() + group.w(),
                group.y() + group.h());

        final var refunds = new IntMap<ItemSeq>();
        final var positions = new IntSeq();
        final var seen = new IntSet();
        var actualGroupSize = 0;
        for (final var element : group.elements()) {
            actualGroupSize += 1
                    + (element.data() instanceof MindustryDisplay data
                            ? data.processors().size()
                            : 0);
            switch (element.data()) {
                case MindustryCanvas _ -> {
                    if (!(anchoredAt(element.x(), element.y(), element.size())
                            instanceof CanvasBlock.CanvasBuild canvas)) {
                        continue;
                    }
                    collect(canvas, positions, seen, refunds);
                }
                case MindustryPixel _ -> {
                    final var building = anchoredAt(element.x(), element.y(), element.size());
                    if (!(building instanceof Sorter.SorterBuild || building instanceof LightBlock.LightBuild)) {
                        continue;
                    }
                    collect(building, positions, seen, refunds);
                }
                case MindustryDisplay data -> {
                    if (!(anchoredAt(element.x(), element.y(), element.size())
                            instanceof LogicDisplay.LogicDisplayBuild display)) {
                        continue;
                    }
                    collect(display, positions, seen, refunds);
                    for (final var link : data.processors().keySet()) {
                        if (!(anchoredAt(element.x() + GeometryUtils.x(link), element.y() + GeometryUtils.y(link))
                                instanceof LogicBlock.LogicBuild processor)) {
                            continue;
                        }
                        collect(processor, positions, seen, refunds);
                    }
                }
            }
        }

        Call.setTileBlocks(Blocks.air, Team.derelict, positions.toArray());
        log.info("Deleted {} buildings out of {}", positions.size, actualGroupSize);

        if (Vars.state.rules.infiniteResources) {
            return;
        }
        for (final var entry : refunds) {
            final var team = Team.get(entry.key);
            if (team.active() && !team.rules().infiniteResources) {
                team.items().add(entry.value);
                log.info("Refunded team {} for deleted buildings: {}", team.name, JsonIO.write(entry.value));
            }
        }
    }

    // The classification is asynchronous, the group may describe buildings that have since been replaced
    private static @Nullable Building anchoredAt(final int x, final int y, final int size) {
        final var building = anchoredAt(x, y);
        return building != null && building.block.size == size ? building : null;
    }

    private static @Nullable Building anchoredAt(final int x, final int y) {
        final var building = Vars.world.build(x, y);
        return building != null
                        && MindustryUtils.anchorTileX(building) == x
                        && MindustryUtils.anchorTileY(building) == y
                ? building
                : null;
    }

    private static void collect(
            final Building building, final IntSeq positions, final IntSet seen, final IntMap<ItemSeq> refunds) {
        if (seen.add(building.pos())) {
            positions.add(building.pos());
            refunds.get(building.team().id, ItemSeq::new).add(building.block.requirements);
        }
    }
}
