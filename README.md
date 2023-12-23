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

- **[ModerateContent](https://moderatecontent.com/)**: Incredibly generous free tier with 10000 free requests per month.

  ```yaml
  analyzer:
    moderate-content-token: xxx
  ```

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
  
  > There is also the in-game command `nohorny-tracker-debug` that allows you to check 
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
is posted every time a cluster of `NoHornyImage` is processed,
see [NoHornyAutoBan](src/main/kotlin/com/xpdustry/nohorny/NoHornyAutoBan.kt) for an example.

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

- `./gradlew runMindustryServer` to run the plugin in a local Mindustry server.

- `./gradlew runMindustryClient` to start a local Mindustry client that will let you test the plugin.

- `./gradlew spotlessApply` to apply the code formatting and the licence header.

## Support

Need a helping hand ? You can talk to the maintainers in the [Chaotic Neutral discord](https://discord.xpdustry.com) in
the `#support` channel.
