// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.struct.IntMap;
import arc.struct.IntSeq;
import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.MindustryDisplay;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.Locale;
import java.util.function.Supplier;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.type.ItemSeq;
import mindustry.world.blocks.logic.CanvasBlock;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AutoModerator implements LifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(AutoModerator.class);

    private final Supplier<Policy> policy = ConfigUtils.registerSafeSettingEntry(
            "nohorny-automod-policy",
            "Then policy to adopt when a group of buildings is classified. Valid values are 'disabled', 'delete_nsfw', 'delete_warn', 'ban_nsfw'.",
            Policy.BAN_NSFW,
            value -> Policy.valueOf(value.toUpperCase(Locale.ROOT)));

    @Override
    public void onInit() {
        MindustryUtils.onEvent(ClassificationEvent.class, this::onClassificationEvent);
    }

    private void onClassificationEvent(final ClassificationEvent event) {
        final var policy = this.policy.get();
        if (policy == Policy.DISABLED) {
            return;
        }
        switch (policy) {
            case BAN_NSFW -> {
                if (event.rating().isWorseOrEqualThan(Rating.NSFW)) {
                    this.delete(event.group());
                    if (event.author() != null) {
                        this.ban(
                                event.author(), event.group().x(), event.group().y());
                    }
                }
            }
            case DELETE_WARN -> {
                if (event.rating().isWorseOrEqualThan(Rating.WARN)) {
                    this.delete(event.group());
                }
            }
            case DELETE_NSFW -> {
                if (event.rating().isWorseOrEqualThan(Rating.NSFW)) {
                    this.delete(event.group());
                }
            }
        }
    }

    private void ban(final MindustryAuthor author, final int groupX, final int groupY) {
        log.info(
                "Banning {} (uuid={},ip={}) for NSFW buildings at ({}, {})",
                Vars.netServer.admins.getInfo(author.uuid()).plainLastName(),
                author.uuid(),
                author.ip(),
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

    private void delete(final VirtualBuilding.Group<? extends MindustryImage> group) {
        log.info(
                "Building group within ({}, {}) and ({}, {}) is unsafe, deleting",
                group.x(),
                group.y(),
                group.x() + group.w(),
                group.y() + group.h());

        final var refunds = new IntMap<ItemSeq>();
        final var positions = new IntSeq();
        for (final var element : group.elements()) {
            switch (element.data()) {
                case MindustryCanvas _ -> {
                    if (!(Vars.world.build(element.x(), element.y()) instanceof CanvasBlock.CanvasBuild canvas)) {
                        continue;
                    }
                    positions.add(canvas.pos());
                    refunds.get(canvas.team().id, ItemSeq::new).add(canvas.block.requirements);
                }
                case MindustryDisplay data -> {
                    if (!(Vars.world.build(element.x(), element.y())
                            instanceof LogicDisplay.LogicDisplayBuild display)) {
                        continue;
                    }
                    positions.add(display.pos());
                    refunds.get(display.team().id, ItemSeq::new).add(display.block.requirements);
                    for (final var link : data.processors().keySet()) {
                        if (!(Vars.world.build(element.x() + GeometryUtils.x(link), element.y() + GeometryUtils.y(link))
                                instanceof LogicBlock.LogicBuild processor)) {
                            continue;
                        }
                        positions.add(processor.pos());
                        refunds.get(processor.team().id, ItemSeq::new).add(processor.block.requirements);
                    }
                }
            }
        }

        Call.setTileBlocks(Blocks.air, Team.derelict, positions.toArray());
        log.info("Deleted {} buildings", group.elements().size());

        if (Vars.state.rules.infiniteResources) {
            return;
        }
        for (final var entry : refunds) {
            final var team = Team.get(entry.key);
            if (team.active() && !team.rules().infiniteResources) {
                team.items().add(entry.value);
                log.info("Refunded team {} for deleted buildings: {}", team.name, entry.value);
            }
        }
    }

    private enum Policy {
        DISABLED,
        DELETE_NSFW,
        DELETE_WARN,
        BAN_NSFW
    }
}
