package fr.xpdustry.nohorny.logic;

import mindustry.gen.Player;
import mindustry.world.Tile;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;
import org.jetbrains.annotations.NotNull;

/**
 * Context for the {@link HornyLogicBuildService}.
 */
public record HornyLogicBuildContext(@NotNull Tile tile, @NotNull String code, @NotNull Player player) {

  public HornyLogicBuildContext(final @NotNull LogicBuild building, final @NotNull Player player) {
    this(building.tile(), building.code, player);
  }
}
