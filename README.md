# nohorny

[![Maven](https://maven.xpdustry.com/api/badge/latest/releases/com/xpdustry/nohorny?color=008080&name=nohorny&prefix=v)](https://maven.xpdustry.com/#/releases/com/xpdustry/nohorny)
[![Downloads](https://img.shields.io/github/downloads/xpdustry/nohorny/total?color=008080)](https://github.com/xpdustry/nohorny/releases)
[![Mindustry 7.0](https://img.shields.io/badge/Mindustry-7.0-008080)](https://github.com/Anuken/Mindustry/releases)
[![Discord](https://img.shields.io/discord/519293558599974912?color=008080&label=Discord)](https://discord.xpdustry.com)

## Description

Are you sick of players turning your awesome server into a NSFW gallery ?
Do you wish to bring back your logic displays without the fear of seing anime girls in questionable situations ?
Well, worry no more, xpdustry cooked another banger plugin for just this situation.

Introducing **nohorny**, the successor of [BMI](https://github.com/L0615T1C5-216AC-9437/BannedMindustryImage).
This mindustry plugin automatically tracks logic displays and canvases and process them when needed 
with the anti-nsfw API of your choice.

Enjoy this family friendly factory building game as the [cat](https://github.com/Anuken) intended it to be.

## Installation

This plugin requires at least :

- Java 17

- Mindustry v146

- [KotlinRuntime](https://github.com/xpdustry/kotlin-runtime) latest

## Usage

Put the plugin in your `config/mods` directory and start your server.

Then, go to the created directory `config/mods/nohorny` and create a file named `config.yaml`.

Now you can set up the analyzer of your choice:

- **[SightEngine](https://sightengine.com/)**: Very nice service with 2000 free operations per month. Also supports gore detection.
 
  ```yaml
  analyzer:
    sight-engine-user: xxx
    sight-engine-secret: xxx
    # Optional thresholds tweaks
    unsafe-threshold: 0.55
    warning-threshold: 0.4
    kinds:
      - "NUDITY"
      # Since gore is very uncommon, it's not enabled by default
      - "GORE"
  ```

- **Debug**: The debug analyzer allows you to check if the plugin properly renders the logic and canvases images,
  by saving them in the directory `config/mods/nohorny/debug`.

  ```yaml
  analyzer: Debug
  ```
  
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

Then you will be able to intercept `ImageAnalyzerEvent`, which is posted every time a cluster of `NoHornyImage` is processed,
see [NoHornyAutoBan](src/main/kotlin/com/xpdustry/nohorny/NoHornyAutoBan.kt) for an example.

## Advanced Configuration

In `config.yaml`:

```yaml
# Whether nohorny should automatically ban players when nsfw is detected,
# set to false if you want to handle that yourself
auto-ban: true
# The delay between the last logic or canvas block built and the analysis step,
# lower it on servers with fast build time such as sandbox
processing-delay: 5s
# Display tracker configuration
displays:
  # The minimum number of draw instructions in a logic processor to be part of a cluster
  minimum-instruction-count: 100
  # The minimum number of logic processors in a cluster to be eligible for processing
  minimum-processor-count: 5
  # The search radius of linked logic processors around a cluster of logic displays,
  # tweak depending on the average size of your server maps
  processor-search-radius: 10
# Canvas tracker configuration
canvases:
  # The minimum number of canvases in a cluster to be eligible for processing,
  # relatively high since you a lot of canvases are needed for a clear picture
  minimum-canvas-cluster-size: 9
```

## Building

- `./gradlew shadowJar` to compile the plugin into a usable jar (will be located at `builds/libs/nohorny.jar`).

- `./gradlew runMindustryServer` to run the plugin in a local Mindustry server.

- `./gradlew runMindustryClient` to start a local Mindustry client that will let you test the plugin.

- `./gradlew spotlessApply` to apply the code formatting and the licence header.

## Support

Need a helping hand ? You can talk to the maintainers in the [Chaotic Neutral discord](https://discord.xpdustry.com) in
the `#support` channel.
