package fr.xpdustry.nohorny.logic;

import mindustry.gen.Player;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;
import org.jetbrains.annotations.NotNull;

public record HornyLogicBuildContext(int x, int y, @NotNull String code, @NotNull Player player) {

  public HornyLogicBuildContext(final @NotNull LogicBuild building, final @NotNull Player player) {
    this(building.tileX(), building.tileY(), building.code, player);
  }
}
