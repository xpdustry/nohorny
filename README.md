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

- Mindustry v155

- Java 25

- [SLF4MD](https://github.com/xpdustry/slf4md) latest

- [SQL4MD](https://github.com/xpdustry/sql4md) latest (optional)

## Usage

Put the plugin in your `config/mods` directory and start your server.

Then, go to the created directory `config/mods/nohorny` and create a file named `config.yaml`.

The plugin has two independent modes:

- `server`: runs the classification HTTP API.

- `client`: tracks displays/canvases and sends them to a remote nohorny server.

You can enable either or both.

### Classifiers

- **ViT**: Run a local Vision Transformer model for classification.

  ```yaml
  server:
    classifiers:
      - type: vit
        file: falconsai_nsfw_image_detection.pt
        labels:
          - normal
          - nsfw
        nsfw-label: nsfw
        thresholds:
          nsfw: 0.7
          warn: 0.4
  ```

- **SightEngine**: Hosted moderation service.

  ```yaml
  server:
    classifiers:
      - type: sight-engine
        user: xxx
        secret: xxx
        thresholds:
          nsfw: 0.7
          warn: 0.5
  ```

- Multiple classifiers can be configured:

  ```yaml
  server:
    classifiers:
      - type: vit
        file: model.pt
        labels:
          - normal
          - nsfw
        nsfw-label: nsfw
        thresholds:
          nsfw: 0.7
          warn: 0.4
      - type: sight-engine
        user: xxx
        secret: xxx
        thresholds:
          nsfw: 0.7
          warn: 0.5
  ```

### Authenticators

Configure the HTTP server authentication for the API:

- **Allow All**: No authentication (not recommended for production).

  ```yaml
  server:
    authenticators:
      - type: allow-all
  ```

- **Localhost**: Only allow connections from localhost.

  ```yaml
  server:
    authenticators:
      - type: localhost
  ```

- **API Key**: Authenticate using persisted API keys. Manage keys with `nohorny-api-keys add|remove|list <label>`. Clients should send the key as `Authorization: Bearer <key>`.

  ```yaml
  server:
    authenticators:
      - type: api-key
  ```

- **Mindustry Server List**: Only allow known Mindustry servers.

  ```yaml
  server:
    authenticators:
      - type: mindustry-server-list
        sources:
          - https://example.com/servers.json
        refresh-interval: 1h
  ```

### Client Mode

The generated default `config.yaml` enables client mode and points to the public nohorny endpoint:

```yaml
client:
  endpoint: https://nohorny.xpdustry.com
  timeout: 10s
  displays:
    processing-threshold: 0.3
    processing-delay: 5s
    minimum-group-size: 1
    minimum-processor-count: 3
    minimum-instruction-count: 100
    processor-search-radius: 10
  canvases:
    processing-threshold: 0.3
    processing-delay: 5s
    minimum-group-size: 9
    minimum-palette-size: 3
  auto-mod:
    ban-on: NSFW
    delete-on: WARN
```

Restart the server after changing the configuration.

### Server Commands

- `nohorny-api-keys <add|remove|list> [label]` - Manage API keys (when api-key authenticator is enabled)

## Developers

Other plugins can depend on nohorny and listen for `ClassificationEvent`.

Add the following to your `build.gradle`:

```gradle
repositories {
    maven { url = uri("https://maven.xpdustry.com/releases") }
}

dependencies {
    compileOnly("com.xpdustry:nohorny:VERSION")
}
```

Then subscribe to `ClassificationEvent` through Mindustry's event bus.

## Advanced Configuration

In `config.yaml`:

```yaml
server:
  host: 127.0.0.1
  port: 8080
  classifiers:
    - type: vit
      file: falconsai_nsfw_image_detection.pt
      labels:
        - normal
        - nsfw
      nsfw-label: nsfw
      thresholds:
        nsfw: 0.7
        warn: 0.4
  authenticators:
    - type: localhost

client:
  endpoint: http://127.0.0.1:17656
  token: ""
  timeout: 30s
  displays:
    processing-threshold: 0.3
    processing-delay: 5s
    minimum-group-size: 1
    minimum-processor-count: 3
    minimum-instruction-count: 100
    processor-search-radius: 10
  canvases:
    processing-threshold: 0.3
    processing-delay: 5s
    minimum-group-size: 9
    minimum-palette-size: 3
  auto-mod:
    delete-on: WARN
    ban-on: NSFW
```

## Building

- `./gradlew shadowJar` to compile the plugin into a usable jar at `build/libs/nohorny.jar`.

- `./gradlew runMindustryServer` to run the plugin in a local Mindustry server.

- `./gradlew runMindustryDesktop` to start a local Mindustry client for testing.

- `./gradlew spotlessApply` to apply the code formatting and the licence header.

## Support

Need a helping hand ? You can talk to the maintainers in the [Chaotic Neutral discord](https://discord.xpdustry.com) in
the `#support` channel.
