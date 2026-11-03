// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Events;
import mindustry.game.EventType;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class NoHornyEventBusTest {

    @Test
    void track_the_connected_players() {
        try (final var events = new NoHornyEventBus()) {
            final var player = player("uuid", "usid");
            Events.fire(new EventType.PlayerJoin(player));
            assertSame(player, events.player(MUUID.from(player)));

            Events.fire(new EventType.PlayerLeave(player));
            assertNull(events.player(MUUID.from(player)));
        }
    }

    @Test
    void track_the_players_sharing_a_uuid() {
        try (final var events = new NoHornyEventBus()) {
            // Mindustry only kicks the players with a uuid already in use in strict mode
            final var first = player("uuid", "first");
            final var second = player("uuid", "second");
            Events.fire(new EventType.PlayerJoin(first));
            Events.fire(new EventType.PlayerJoin(second));

            Events.fire(new EventType.PlayerLeave(second));
            assertSame(first, events.player(MUUID.from(first)));
            assertNull(events.player(MUUID.from(second)));
        }
    }

    private static Player player(final String uuid, final String usid) {
        final var player = Player.create();
        player.con = new NetConnection("0.0.0.0") {
            @Override
            public void send(final Object object, final boolean reliable) {}

            @Override
            public void close() {}
        };
        player.con.uuid = uuid;
        player.con.usid = usid;
        return player;
    }
}
