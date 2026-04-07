// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.struct.IntSeq;
import com.xpdustry.nohorny.common.classification.Rating;
import com.xpdustry.nohorny.common.image.MindustryCanvas;
import com.xpdustry.nohorny.common.image.MindustryDisplay;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.world.blocks.logic.CanvasBlock;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;

final class AutoModerator implements LifecycleListener {

    private final Supplier<Policy> policy = ConfigUtils.registerSafeSettingEntry(
            "nohorny-automod-policy",
            "Then policy to adopt when a group of buildings is classified. Valid values are 'disabled', 'delete_nsfw', 'delete_warn', 'ban_nsfw'.",
            Policy.BAN_NSFW,
            value -> Policy.valueOf(value.toUpperCase(Locale.ROOT)));

    @Override
    public void onInit() {
        MindustryUtils.onEvent(ClassificationEvent.class, this::onClassificationEvent);
    }

    @SuppressWarnings({"fallthrough", "MissingCasesInEnumSwitch"})
    private void onClassificationEvent(final ClassificationEvent event) {
        var delete = false;

        switch (this.policy.get()) {
            case BAN_NSFW:
                if (event.rating().isWorseOrEqualThan(Rating.NSFW) && event.author() != null) {
                    Vars.netServer.admins.banPlayerIP(event.author().ip());
                    Vars.netServer.admins.banPlayer(event.author().uuid());
                    for (final var player : Groups.player) {
                        if (player.uuid().equals(event.author().uuid())
                                || player.ip().equals(event.author().ip())) {
                            player.kick("[scarlet]You have been banned for building NSFW buildings.");
                            Call.sendMessage("[pink][[NoHorny]: [white]" + player.plainName()
                                    + " has been banned for NSFW buildings at "
                                    + event.group().x() + ", "
                                    + event.group().y() + ".");
                        }
                    }
                }
            case DELETE_WARN:
                delete = event.rating().isWorseOrEqualThan(Rating.WARN);
                break;
            case DELETE_NSFW:
                delete = event.rating().isWorseOrEqualThan(Rating.NSFW);
                break;
        }

        if (!delete) {
            return;
        }

        final var positions = new IntSeq();
        for (final var element : event.group().elements()) {
            switch (element.data()) {
                case MindustryCanvas _ -> {
                    if (!(Vars.world.build(element.x(), element.y()) instanceof CanvasBlock.CanvasBuild canvas)) {
                        continue;
                    }
                    positions.add(canvas.pos());
                    if (!Vars.state.rules.infiniteResources && canvas.team().active()) {
                        canvas.team().items().add(Arrays.asList(canvas.block.requirements));
                    }
                }
                case MindustryDisplay data -> {
                    if (!(Vars.world.build(element.x(), element.y())
                            instanceof LogicDisplay.LogicDisplayBuild display)) {
                        continue;
                    }
                    positions.add(display.pos());
                    if (!Vars.state.rules.infiniteResources && display.team().active()) {
                        display.team().items().add(Arrays.asList(display.block.requirements));
                    }
                    for (final var link : data.processors().keySet()) {
                        if (!(Vars.world.build(element.x() + link.x(), element.y() + link.y())
                                instanceof LogicBlock.LogicBuild processor)) {
                            continue;
                        }
                        positions.add(processor.pos());
                        if (!Vars.state.rules.infiniteResources
                                && processor.team().active()) {
                            processor.team().items().add(Arrays.asList(processor.block.requirements));
                        }
                    }
                }
            }
        }

        Call.setTileBlocks(Blocks.air, Team.derelict, positions.toArray());
    }

    private enum Policy {
        DISABLED,
        DELETE_NSFW,
        DELETE_WARN,
        BAN_NSFW
    }
}
