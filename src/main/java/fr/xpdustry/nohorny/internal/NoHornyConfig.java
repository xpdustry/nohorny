package fr.xpdustry.nohorny.internal;

import org.aeonbits.owner.Accessible;
import org.jetbrains.annotations.NotNull;

public interface NoHornyConfig extends Accessible {

  @DefaultValue("1000")
  @Key("nohorny.logic-build.cache-size")
  int getLogicBuildCacheSize();

  @DefaultValue("false")
  @Key("nohorny.logic-build.deep-search")
  boolean isDeepSearchEnabled();

  @DefaultValue("KICK")
  @Key("nohorny.logic-build.default-action")
  @NotNull HornyLogicBuildAction getDefaultAction();
}
