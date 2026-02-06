// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.tracking;

import com.xpdustry.nohorny.NoHornyListener;
import com.xpdustry.nohorny.event.EventSubscriptionManager;
import com.xpdustry.nohorny.geometry.ImmutablePoint2;
import com.xpdustry.nohorny.geometry.VirtualBuilding;
import com.xpdustry.nohorny.image.MindustryDisplay;
import com.xpdustry.nohorny.image.MindustryImage;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.world.blocks.logic.CanvasBlock;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;

public final class AutoModerator implements NoHornyListener {

    private final EventSubscriptionManager events = new EventSubscriptionManager();
    private final AutoModeratorConfig config;

    public AutoModerator(final AutoModeratorConfig config) {
        this.config = config;
    }

    @Override
    public void onInit() {
        this.events.subscribe(ClassificationEvent.class, this::onClassificationEvent);
    }

    private void onClassificationEvent(final ClassificationEvent event) {
        var warn = true;
        if (this.config.banOn() != null
                && event.classification().ordinal() <= this.config.banOn().ordinal()
                && event.author() != null) {
            warn = false;
            for (final var player : Groups.player) {
                if (player.uuid().equals(event.author().uuid())
                        || InetAddress.ofLiteral(player.ip())
                                .equals(event.author().address())) {
                    Vars.netServer.admins.banPlayer(player.uuid());
                    player.kick("[scarlet]You have been banned for building NSFW buildings.");
                    Call.sendMessage("[pink]NoHorny: [white]" + player.plainName()
                            + " has been banned for NSFW buildings at "
                            + event.group().x() + ", " + event.group().y() + ".");
                }
            }
        }

        if (this.config.deleteOn() != null
                && event.classification().ordinal() <= this.config.deleteOn().ordinal()) {
            for (final var point : this.extractPoints(event.group())) {
                final var building = Vars.world.build(point.x(), point.y());
                if (building instanceof LogicDisplay.LogicDisplayBuild
                        || building instanceof LogicBlock.LogicBuild
                        || building instanceof CanvasBlock.CanvasBuild) {
                    final var tile = Vars.world.tile(point.x(), point.y());
                    if (tile == null) {
                        continue;
                    }
                    final var team = tile.team();
                    tile.setNet(Blocks.air);
                    if (!Vars.state.rules.infiniteResources && team.active()) {
                        for (final var stack : building.block.requirements) {
                            team.items().add(stack.item, stack.amount);
                        }
                    }
                }
            }
            if (warn) {
                Call.sendMessage("[pink]NoHorny: [white]Possible NSFW buildings deleted at "
                        + event.group().x() + ", " + event.group().y() + ".");
            }
        }
    }

    private Set<ImmutablePoint2> extractPoints(final VirtualBuilding.Group<? extends MindustryImage> group) {
        final var points = new HashSet<ImmutablePoint2>();
        for (final var element : group.elements()) {
            points.add(new ImmutablePoint2(element.x(), element.y()));
            if (element.data() instanceof MindustryDisplay display) {
                for (final var processorPoint : display.processors().keySet()) {
                    points.add(new ImmutablePoint2(processorPoint.x(), processorPoint.y()));
                }
            }
        }
        return points;
    }
}
