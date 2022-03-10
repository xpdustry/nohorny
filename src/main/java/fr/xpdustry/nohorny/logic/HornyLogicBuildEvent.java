package fr.xpdustry.nohorny.logic;

import mindustry.gen.Player;
import org.jetbrains.annotations.NotNull;

public record HornyLogicBuildEvent(@NotNull HornyLogicBuildType type, @NotNull Player player) {

}
