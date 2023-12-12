# nohorny

[![Xpdustry latest](https://maven.xpdustry.com/api/badge/latest/releases/com/xpdustry/nohorny?color=00FFFF&name=nohorny&prefix=v)](https://github.com/xpdustry/nohorny/releases)
[![Build status](https://github.com/xpdustry/nohorny/actions/workflows/build.yml/badge.svg?branch=master&event=push)](https://github.com/xpdustry/nohorny/actions/workflows/build.yml)
[![Mindustry 7.0 ](https://img.shields.io/badge/Mindustry-7.0-ffd37f)](https://github.com/Anuken/Mindustry/releases)
[![Discord](https://img.shields.io/discord/519293558599974912?color=00b0b3&label=Discord)](https://discord.xpdustry.com)

## Description

Are you sick of players turning your awesome server into a NSFW gallery ?
Do you wish to bring back your logic displays without the fear of seing anime girls in questionable situations ?
Well, worry no more, xpdustry cooked another banger plugin for just this situation.

Introducing **nohorny 2**, the successor of [BMI](https://github.com/L0615T1C5-216AC-9437/BannedMindustryImage).
This mindustry plugin automatically tracks logic displays and canvases and process them when needed 
with the anti-nsfw API of your choice.

Enjoy this family friendly factory building game as the [cat](https://github.com/Anuken) intended it to be.

## Usage

Put the plugin in your `config/mods` directory and start your server.

Then, go to the created directory `config/mods/nohorny` and create a file named `config.yaml`.

Now you can set up the analyzer of your choice:

- **[SightEngine](https://sightengine.com/)**: Very nice service with a rather generous free tier
  (2000 operations per month). No credit card required.
 
  ```yaml
  analyzer:
    sight-engine-user: xxx
    sight-engine-secret: xxx
    # Optional thresholds tweaks
    unsafe-threshold: 0.55
    warning-threshold: 0.4
    kinds:
      - "NUDITY"
      # SightEngine also support gore detection, but is very uncommon in mindustry
      # That's why it's not enabled by default
      - "GORE"
  ```

- **Debug**: The debug analyzer allows you to check if the plugin properly renders the logic and canvases images,
  by saving them in the directory `config/mods/nohorny/debug`.

  ```yaml
  analyzer: Debug
  ```
  
  > For more detailed debugging, enable debug logging with the command `config debug true` 
  and distributor trace logging with `config trace true`.
  There is also the in-game command `nohorny-tracker-debug` that allows you to check 
  if displays and canvases are properly tracked.
  
Once you chose your analyzer, load your changes using the command `nohorny-reload` in the console, and enjoy,
the plugin will automatically ban players that have built structures at `UNSAFE` Rating.

## Developers

For those of you who want more control, like implementing a validation system to avoid false positives.

I suggest you to use the nohorny API in your plugin.

To do so, add the following in your `build.gradle`

```gradle
repositories {
    maven { url = uri("https://maven.xpdustry.com/releases") }
}

dependencies {
    compileOnly("com.xpdustry:nohorny:VERSION")
}
```

Then you will be able to intercept `ImageAnalyzerEvent`, which 
is posted every time a cluster of `NoHornyImage` is processed:

```java
import arc.Events;
import arc.util.Log;
import com.xpdustry.nohorny.analyzer.ImageAnalyzerEvent;
import com.xpdustry.nohorny.NoHornyImage;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.mod.Plugin;

public final class MyPlugin extends Plugin {

  @Override
  public void init() {
    Events.on(ImageAnalyzerEvent.class, event -> {
      switch (event.getResult().getRating()) {
        case WARNING -> {
          Log.info("The @ cluster is kinda sus",
            event.getCluster().getIdentifier());
        }

        case UNSAFE -> {
          Log.info("That's it, to the horny jail",
            event.getCluster().getIdentifier());
          final NoHornyImage.Author author = event.getAuthor();

          if (author == null) {
            return;
          }

          Groups.player.forEach(player -> {
            if (player.uuid().equals(author.getUuid()) ||
              player.ip().equals(author.getAddress().getHostAddress())) {
              Vars.netServer.admins.banPlayer(player.uuid());
              player.kick("[scarlet]You have been banned for building a NSFW building.");
            }
          });
        }
      }
    });
  }
}
```

> If you handle the ban of the player yourself (like in the above example),
> you should disable the auto ban of nohorny by adding the following in the config.
> ```yaml
> auto-ban: false
> ```

## Installation

This plugin requires :

- Java 17 or above

- Mindustry v146 or above

- [KotlinRuntime](https://github.com/xpdustry/kotlin-runtime) v3.1.0-k.1.9.10

- [Distributor](https://github.com/xpdustry/distributor) v3.2.1

## Building

- `./gradlew shadowJar` to compile the plugin into a usable jar (will be located at `builds/libs/nohorny.jar`).

- `./gradlew jar` for a plain jar that contains only the plugin code.

- `./gradlew runMindustryServer` to run the plugin in a local Mindustry server.

- `./gradlew runMindustryClient` to start a local Mindustry client that will let you test the plugin.

- `./gradlew spotlessApply` to apply the code formatting and the licence header.

- `./gradlew dependencyUpdates` to check for dependency updates.

## Support

Need a helping hand ? You can talk to the maintainers in the [Chaotic Neutral discord](https://discord.xpdustry.com) in
the `#support` channel.
