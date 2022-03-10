package fr.xpdustry.nohorny.logic;

import mindustry.gen.Player;
import org.jetbrains.annotations.NotNull;

public record HornyLogicBuildEvent(@NotNull HornyLogicBuildType type, @NotNull Player player) {
  public HornyLogicBuildEvent {
    if (type == HornyLogicBuildType.NOT_HORNY)
      throw new IllegalArgumentException("Only fire this event when the type is 'horny' or 'nudity'.");
  }
}
