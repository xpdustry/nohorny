package fr.xpdustry.nohorny.logic;

import mindustry.gen.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Event thrown when a player build a logic build containing NSFW stuff.
 */
public record HornyLogicBuildEvent(@NotNull Player player) {

}
