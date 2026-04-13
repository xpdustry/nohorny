# nohorny

[![Maven](https://maven.xpdustry.com/api/badge/latest/releases/com/xpdustry/nohorny-client?color=008080&name=nohorny&prefix=v)](https://maven.xpdustry.com/#/releases/com/xpdustry/nohorny-client)
[![Downloads](https://img.shields.io/github/downloads/xpdustry/nohorny/total?color=008080)](https://github.com/xpdustry/nohorny/releases)
[![Mindustry 8.0](https://img.shields.io/badge/Mindustry-8.0-008080)](https://github.com/Anuken/Mindustry/releases)
[![Discord](https://img.shields.io/discord/519293558599974912?color=008080&label=Discord)](https://discord.xpdustry.com)

## Description

Are you sick of players turning your awesome server into a NSFW gallery ?
Do you wish to bring back your logic displays without the fear of seing anime girls in questionable situations ?
Well, worry no more, xpdustry cooked another banger plugin for just this situation.

Introducing **nohorny**, the successor of [BMI](https://github.com/L0615T1C5-216AC-9437/BannedMindustryImage).
This project consists of a Mindustry plugin client that automatically tracks logic displays and canvases,
and a standalone classification server that processes them.

Enjoy this family friendly factory building game as the [cat](https://github.com/Anuken) intended it to be.

## Installation

### Client (Mindustry Plugin)

This plugin requires at least:

- Mindustry 157
- Java 25
- [SLF4MD](https://github.com/xpdustry/slf4md) latest

Put `nohorny-client.jar` in your `config/mods` directory and start your server.

### Server (Classification API)

This is a standalone Java application requiring:

- Java 25

Then, you can simply run `java -jar nohorny-server.jar`.

## Client Configuration

The client uses Mindustry's built-in server configuration system. Use the `config` command in the server console:

| Key | Description | Default |
| --- | --- | --- |
| `nohorny-api-endpoint` | Base URL used by the plugin. The client resolves `status` and `classify` relative to it. | `https://nohorny.xpdustry.com/api` |
| `nohorny-automod-policy` | The policy to apply when a group of buildings is classified. | `BAN_NSFW` |
| `nohorny-debug-tap` | Enables admin double-tap debugging for tracked displays and canvases. | `false` |

### Auto-Mod Policies

| Policy | Behavior |
| --- | --- |
| `DISABLED` | No action taken. |
| `DELETE_NSFW` | Delete buildings rated NSFW. |
| `DELETE_WARN` | Delete buildings rated WARN or NSFW. |
| `BAN_NSFW` | Ban the author and delete buildings rated WARN or NSFW. |

### Example

```text
config nohorny-api-endpoint http://127.0.0.1:8080/
config nohorny-automod-policy ban_nsfw
config nohorny-debug-tap true
```

## Server Configuration

In most cases, just run `nohorny-server.jar`. The bundled defaults are already set up for the built-in classifier and
for serving the API on port `8080`.

For localhost deployments, the server exposes `GET /status` and `POST /classify` at the root by default, so the client
should usually point to `http://127.0.0.1:8080/`. If you want the server to live under `/api` instead, set
`server.servlet.context-path=/api`.

Spring Boot lets you override settings in a few different ways. For example, these all change the server port to
`9090`:

```properties
# application.properties
server.port=9090
```

```text
SERVER_PORT=9090 java -jar nohorny-server.jar
```

```text
java -jar nohorny-server.jar --server.port=9090
```

### Properties

The server currently exposes:

| Property | Description | Default |
| --- | --- | --- |
| `server.address` | Bind address for the HTTP server. | Spring Boot default |
| `server.port` | HTTP port. | `8080` |
| `server.servlet.context-path` | Optional path prefix for all endpoints. | Empty |
| `nohorny.classifier.vit.repository` | Hugging Face repository to download the model from. | `phinner/nsfw_image_detection` |
| `nohorny.classifier.vit.revision` | Repository revision or commit to download. | `aab8ebd004112c8358a8ff9709c0492c2ba96bdc` |
| `nohorny.classifier.vit.file` | Model file to load. | `falconsai_nsfw_image_detection.pt` |
| `nohorny.classifier.vit.token` | Hugging Face token for private or gated models. | None |
| `nohorny.classifier.vit.labels` | Output labels returned by the model. | `normal,nsfw` |
| `nohorny.classifier.vit.nsfw-label` | Which label is interpreted as NSFW. | `nsfw` |
| `nohorny.classifier.vit.directory` | Directory where the downloaded model is cached. | `.cached-models` |
| `nohorny.classifier.vit.engine` | DJL engine name used to load the model. | `PyTorch` |
| `nohorny.classifier.vit.thresholds.warn` | Confidence threshold for a `WARN` rating. | `0.4` |
| `nohorny.classifier.vit.thresholds.nsfw` | Confidence threshold for an `NSFW` rating. | `0.7` |

The model is downloaded from Hugging Face on first startup and cached under `.cached-models/`.

## Client Debugging

Set `nohorny-debug-tap` to `true` to enable admin-only debugging. When enabled, double-tapping a tracked display or
canvas group labels the detected group in-game and dumps a rendered PNG to `config/mods/nohorny/debug/`.

## Developers

Other plugins can depend on nohorny-client and listen for [`ClassificationEvent`](nohorny-client/src/main/java/com/xpdustry/nohorny/client/ClassificationEvent.java).

Add the following to your `build.gradle`:

```gradle
repositories {
    maven { url = uri("https://maven.xpdustry.com/releases") }
}

dependencies {
    compileOnly("com.xpdustry:nohorny-common:VERSION")
    compileOnly("com.xpdustry:nohorny-client:VERSION")
}
```

Then subscribe to [`ClassificationEvent`](nohorny-client/src/main/java/com/xpdustry/nohorny/client/ClassificationEvent.java) through Mindustry's event bus:

```java
import arc.Events;
import mindustry.mod.Plugin;

public final class MyPlugin extends Plugin {

    @Override
    public void init() {
        Events.on(ClassificationEvent.class, event -> {
            System.out.println(
                    "The group at " + event.group().x() + ", " + event.group().y() + " has been classified");
        });
    }
}
```

## Building

- `./gradlew shadowJar` to compile all modules into jars at `nohorny-client/build/libs/nohorny-client.jar` and `nohorny-server/build/libs/nohorny-server.jar`.

- `./gradlew runMindustryServer` to run the client plugin in a local Mindustry server.

- `./gradlew spotlessApply` to apply the code formatting and the license header.

## Support

Need a helping hand ? You can talk to the maintainers in the [Chaotic Neutral discord](https://discord.xpdustry.com) in
the `#support` channel.
