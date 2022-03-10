package fr.xpdustry.nohorny;

import arc.Events;
import arc.util.Log;
import fr.xpdustry.distributor.Distributor;
import fr.xpdustry.distributor.plugin.AbstractPlugin;
import fr.xpdustry.nohorny.logic.HornyLogicBuildContext;
import fr.xpdustry.nohorny.logic.HornyLogicBuildEvent;
import fr.xpdustry.nohorny.logic.HornyLogicBuildService;
import fr.xpdustry.nohorny.logic.HornyLogicBuildType;
import fr.xpdustry.nohorny.logic.impl.GlobalImageBanService;
import io.leangen.geantyref.TypeToken;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.world.blocks.logic.LogicBlock;

@SuppressWarnings("unused")
public final class NoHornyPlugin extends AbstractPlugin {

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void init() {
    Distributor.getServicePipeline()
      .registerServiceType(
        TypeToken.get(HornyLogicBuildService.class),
        GlobalImageBanService.getInstance()
      );

    Events.on(BlockBuildEndEvent.class, event -> {
      if (event.breaking || event.unit == null || event.unit.getPlayer() == null) return;

      final var player = event.unit.getPlayer();
      if (event.tile.build instanceof LogicBlock.LogicBuild building) {
        building.configure(event.config);

        final var context = new HornyLogicBuildContext(building, player);
        final var future = Distributor.getServicePipeline()
          .pump(context)
          .through(HornyLogicBuildService.class)
          .getResultAsynchronously();

        future.whenComplete((type, throwable) -> {
          if (type != HornyLogicBuildType.NOT_HORNY) {
            Log.debug("GIB: Hit @ at (@, @)", player, context.x(), context.y());
            Events.fire(new HornyLogicBuildEvent(type, player));
          } else {
            Log.debug("GIB: Miss @ at (@, @)", player, context.x(), context.y());
          }
        });
      }
    });
  }
}
