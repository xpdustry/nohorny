// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.MindustryAuthor;
import mindustry.gen.Player;

// The identity of a connected player, the uuid alone is shared by the players connected twice when strict mode is off
record MUUID(String uuid, String usid) {

    static MUUID from(final Player player) {
        return new MUUID(player.uuid(), player.usid());
    }

    static MUUID from(final MindustryAuthor author) {
        return new MUUID(author.uuid(), author.usid());
    }
}
