// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.image;

import java.net.InetAddress;
import mindustry.gen.Player;

public record MindustryAuthor(String uuid, String usid, InetAddress address) {
    public MindustryAuthor(final Player player) {
        this(player.uuid(), player.usid(), InetAddress.ofLiteral(player.ip()));
    }
}
