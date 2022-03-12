package fr.xpdustry.nohorny;

import arc.Events;
import fr.xpdustry.distributor.Distributor;
import fr.xpdustry.distributor.plugin.AbstractPlugin;
import fr.xpdustry.nohorny.internal.NoHornyConfig;
import fr.xpdustry.nohorny.logic.HornyLogicBuildContext;
import fr.xpdustry.nohorny.logic.HornyLogicBuildEvent;
import fr.xpdustry.nohorny.logic.HornyLogicBuildService;
import fr.xpdustry.nohorny.logic.HornyLogicBuildType;
import fr.xpdustry.nohorny.logic.impl.GlobalImageBanService;
import io.leangen.geantyref.TypeToken;
import mindustry.Vars;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.world.blocks.logic.LogicBlock;
import net.mindustry_ddns.store.FileStore;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public final class NoHornyPlugin extends AbstractPlugin {

  @SuppressWarnings("NullAway.Init")
  private static FileStore<NoHornyConfig> config;

  /**
   * Returns the config of the plugin.
   */
  public static @NotNull NoHornyConfig config() {
    return config.get();
  }

  @SuppressWarnings({"FutureReturnValueIgnored", "MissingCasesInEnumSwitch"})
  @Override
  public void init() {
    config = getStoredConfig("config", NoHornyConfig.class);

    Distributor.getServicePipeline()
      .registerServiceType(
        TypeToken.get(HornyLogicBuildService.class),
        new GlobalImageBanService(config.get().isDeepSearchEnabled(), config.get().getLogicBuildCacheSize())
      );

    Events.on(BlockBuildEndEvent.class, event -> {
      if (event.breaking || event.unit == null || event.unit.getPlayer() == null) return;

      final var player = event.unit.getPlayer();
      if (event.tile.build instanceof LogicBlock.LogicBuild building) {
        building.configure(event.config);

        final var future = Distributor.getServicePipeline()
          .pump(new HornyLogicBuildContext(building, player))
          .through(HornyLogicBuildService.class)
          .getResultAsynchronously();

        future.whenComplete((type, throwable) -> {
          if (type != HornyLogicBuildType.NOT_HORNY) Events.fire(new HornyLogicBuildEvent(player));
        });
      }
    });

    switch (config.get().getDefaultAction()) {
      case KICK -> Events.on(HornyLogicBuildEvent.class, event -> {
        event.player().kick("You have been kicked for building [pink]NSFW[] logic.");
      });

      case BAN -> Events.on(HornyLogicBuildEvent.class, event -> {
        Vars.netServer.admins.banPlayer(event.player().uuid());
        event.player().kick("You have been banned for building [pink]NSFW[] logic.");
      });
    }
  }
}
