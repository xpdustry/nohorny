# nohorny

[![Maven](https://maven.xpdustry.com/api/badge/latest/releases/com/xpdustry/nohorny?color=008080&name=nohorny&prefix=v)](https://maven.xpdustry.com/#/releases/com/xpdustry/nohorny)
[![Downloads](https://img.shields.io/github/downloads/xpdustry/nohorny/total?color=008080)](https://github.com/xpdustry/nohorny/releases)
[![Mindustry 156.2](https://img.shields.io/badge/Mindustry-156.2-008080)](https://github.com/Anuken/Mindustry/releases)
[![Discord](https://img.shields.io/discord/519293558599974912?color=008080&label=Discord)](https://discord.xpdustry.com)

## Description

Are you sick of players turning your awesome server into a NSFW gallery ?
Do you wish to bring back your logic displays without the fear of seing anime girls in questionable situations ?
Well, worry no more, xpdustry cooked another banger plugin for just this situation.

Introducing **nohorny**, the successor of [BMI](https://github.com/L0615T1C5-216AC-9437/BannedMindustryImage).
This project consists of a Mindustry plugin client that automatically tracks logic displays and canvases,
and a standalone classification server that processes them with a Vision Transformer model.

Enjoy this family friendly factory building game as the [cat](https://github.com/Anuken) intended it to be.

## Architecture

The project is split into three modules:

- **nohorny-common**: Shared library with classification types, image handling, and geometry utilities.
- **nohorny-client**: The Mindustry plugin. Tracks displays/canvases in-game and sends them to a nohorny server for classification.
- **nohorny-server**: A standalone Java HTTP server that loads a ViT model and exposes a classification API.

## Installation

### Client (Mindustry Plugin)

This plugin requires at least:

- Mindustry 156.2
- Java 25
- [SLF4MD](https://github.com/xpdustry/slf4md) latest

Put `nohorny-client.jar` in your `config/mods` directory and start your server.

### Server (Classification API)

This is a standalone Java application requiring:

- Java 25

Run `nohorny-server.jar` with a `config.json` file in the working directory (or set the `NH_CONFIG_FILE_PATH` environment variable).

## Client Configuration

The client uses Mindustry's built-in server configuration system. Use the `config` command in the server console:

- **`nohorny-api-endpoint`** - The NoHorny API endpoint to query for image rating. Default: `https://nohorny.xpdustry.com/api`
- **`nohorny-automod-policy`** - The policy to adopt when a group of buildings is classified. Default: `BAN_NSFW`

### Auto-Mod Policies

| Policy | Behavior |
| --- | --- |
| `DISABLED` | No action taken. |
| `DELETE_NSFW` | Delete buildings rated NSFW. |
| `DELETE_WARN` | Delete buildings rated WARN or NSFW. |
| `BAN_NSFW` | Ban the author and delete buildings rated WARN or NSFW. |

### Example

```
config nohorny-api-endpoint http://127.0.0.1:8080/api
config nohorny-automod-policy ban_nsfw
```

## Server Configuration

Create a `config.json` file:

```json
{
  "server": {
    "host": "localhost",
    "port": 8080
  },
  "classifier": {
    "repository": "Falconsai/nsfw_image_detection",
    "revision": "main",
    "file": "falconsai_nsfw_image_detection.pt",
    "labels": ["normal", "nsfw"],
    "nsfwLabel": "nsfw",
    "thresholds": {
      "nsfw": 0.7,
      "warn": 0.4
    }
  }
}
```

### Classifier Options

The server supports a single **ViT** (Vision Transformer) classifier, loaded via [DJL](https://djl.ai).

| Field | Description | Default |
| --- | --- | --- |
| `repository` | HuggingFace model repository (e.g. `Falconsai/nsfw_image_detection`) | Required |
| `revision` | Git revision to download from | `"main"` |
| `file` | Model filename to download | Required |
| `token` | HuggingFace API token for private models | None |
| `labels` | Classification labels the model outputs | Required |
| `nsfwLabel` | Which label counts as NSFW | Required |
| `thresholds.nsfw` | Confidence threshold for NSFW rating | Required |
| `thresholds.warn` | Confidence threshold for WARN rating (must be less than `nsfw`) | Required |
| `engine` | Inference engine: `PYTORCH` or `ONNX` | `PYTORCH` |

The model is downloaded from HuggingFace on first startup and cached in `.nohorny-model-cache/`.

### Server Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/v4/status` | Health check, returns `"ok"` |
| `POST` | `/v4/classify` | Classify an image, returns `ClassificationResponse` |

## Client Commands

- `nohorny-debug` - Toggle debug overlays and image dumping (labels building groups, double-tap to render group images to `config/mods/nohorny/debug/`)

## Developers

Other plugins can depend on nohorny-client and listen for `ClassificationEvent`.

Add the following to your `build.gradle`:

```gradle
repositories {
    maven { url = uri("https://maven.xpdustry.com/releases") }
}

dependencies {
    compileOnly("com.xpdustry:nohorny-client:VERSION")
}
```

Then subscribe to `ClassificationEvent` through Mindustry's event bus.

### ClassificationEvent

```java
public record ClassificationEvent(
    VirtualBuilding.Group<? extends MindustryImage> group,
    Rating rating,
    @Nullable MindustryAuthor author
) {}
```

- `group` - The building group that was classified.
- `rating` - One of `NSFW`, `WARN`, or `SAFE`.
- `author` - The most likely author (uuid + ip), nullable if undetermined.

## Building

- `./gradlew shadowJar` to compile all modules into jars at `nohorny-client/build/libs/nohorny-client.jar` and `nohorny-server/build/libs/nohorny-server.jar`.

- `./gradlew runMindustryServer` to run the client plugin in a local Mindustry server.

- `./gradlew spotlessApply` to apply the code formatting and the licence header.

## Support

Need a helping hand ? You can talk to the maintainers in the [Chaotic Neutral discord](https://discord.xpdustry.com) in
the `#support` channel.
