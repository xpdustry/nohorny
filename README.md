# NoHorny

<p align="center">
  <img src=".github/nohorny.svg" alt="NoHorny logo" width="388">
</p>

[![Maven](https://maven.xpdustry.com/api/badge/latest/releases/com/xpdustry/nohorny-client?color=008080&name=nohorny&prefix=v)](https://maven.xpdustry.com/#/releases/com/xpdustry/nohorny-client)
[![Downloads](https://img.shields.io/github/downloads/xpdustry/nohorny/total?color=008080)](https://github.com/xpdustry/nohorny/releases)
[![Mindustry 8.0](https://img.shields.io/badge/Mindustry-8.0-008080)](https://github.com/Anuken/Mindustry/releases)
[![Discord](https://img.shields.io/discord/519293558599974912?color=008080&label=Discord)](https://discord.xpdustry.com)

## Description

Are you sick of players turning your awesome Mindustry server into a NSFW gallery?
Do you wish to bring back your logic displays without the fear of seing anime girls in questionable positions?

Introducing **NoHorny**, your autonomous NSFW moderation plugin.
It can detect NSFW logic displays, canvases, sorter and illuminator pixel art, and ban the offending players.

Enjoy this family friendly factory building game as the [cat](https://github.com/Anuken) intended it to be.

## Client (Mindustry Plugin)

### Installation

This plugin requires at least:

- Mindustry 159
- Java 25
- [SLF4MD](https://github.com/xpdustry/slf4md) latest (optional)

Put [`nohorny-client.jar`](https://github.com/xpdustry/nohorny/releases/latest) in your `config/mods` directory and start your mindustry server.

Now, players placing unsafe buildings will be automatically banned.
Then the buildings will be deleted and refunded to the player's team.

### Configuration

You can configure NoHorny using the Mindustry built-in `config` command in your server console, with `config key value`.

#### Available settings

| Key                             | Description                                                                                                                                                               | Default Value                      |
|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|
| `nohorny-auto-mod-policy`       | The policy to apply when a group of buildings is classified.                                                                                                              | `BAN_NSFW`                         |
| `nohorny-api-endpoint`          | Base URL used by the plugin. The client resolves `status` and `classify` relative to it.                                                                                  | `https://nohorny.xpdustry.com/api` |
| `nohorny-api-auth-type`         | HTTP auth mode for the API. Valid values: `DISABLED`, `BASIC`, `BEARER`.                                                                                                  | `DISABLED`                         |
| `nohorny-api-auth-value`        | Auth payload. For `BASIC`, use `username:password`. For `BEARER`, use the raw token.                                                                                      | empty                              |
| `nohorny-discord-webhook`       | Discord webhook used to send alerts when unsafe buildings are detected.                                                                                                   | empty                              |
| `nohorny-discord-webhook-name`  | Username used for messages sent through the Discord webhook.                                                                                                              | `NoHorny`                          |
| `nohorny-discord-webhook-proxy` | Whether discord requests should be proxied. Useful if discord is banned in the host country of your servers. Uses [ProxyScrape](https://proxyscrape.com/free-proxy-list). | `false`                            |
| `nohorny-debug-tap`             | Enables admin double-tap debugging for tracked displays, canvases, sorters and illuminators.                                                                              | `false`                            |

#### Auto-Mod Policies

| Policy        | Behavior                                        |
|---------------|-------------------------------------------------|
| `DISABLED`    | No action taken.                                |
| `DELETE_NSFW` | Delete buildings rated NSFW.                    |
| `DELETE_WARN` | Delete buildings rated WARN or NSFW.            |
| `BAN_NSFW`    | Ban the author and delete buildings rated NSFW. |

#### Discord Webhook

You can set up a discord webhook to be alerted when a NSFW building is detected.

Just configure the webhook url using `config nohorny-discord-webhook https://discord.com/api/webhooks/999999/abcdefgh`.

![discord example](.github/discord-example.png)

Checkout [MAD](https://github.com/phinner/mad) if you want to automatically delete the alerts.

> [!Note]
> 
> If discord is banned in your country, run `config nohorny-discord-webhook-proxy true`.
>
> NoHorny will try to find a suitable proxy to send the alerts from.

#### Debugging

Set `nohorny-debug-tap` to `true` to enable admin-only debugging. When enabled, double-tapping a tracked display,
canvas, sorter or illuminator group labels the detected group in-game, then creates a PNG render and binary dump in `config/mods/nohorny/debug/`.

### Developing

You can integrate your plugin with the NoHorny client by just adding the following to your `build.gradle`:

```gradle
repositories {
    maven { url = uri("https://maven.xpdustry.com/releases") }
}

dependencies {
    compileOnly("com.xpdustry:nohorny-common:VERSION")
    compileOnly("com.xpdustry:nohorny-client:VERSION")
}
```

You will then be able to:

- Subscribe to [classifications events](nohorny-client/src/main/java/com/xpdustry/nohorny/client/ClassificationEvent.java) to handle unsafe buildings with your own logic:

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

- Programmatically configure NoHorny using its [setting system](nohorny-client/src/main/java/com/xpdustry/nohorny/client/NoHornySetting.java):

```java
import com.xpdustry.nohorny.client.AutoModeratorPolicy;
import com.xpdustry.nohorny.client.NoHornyClientAuthType;
import com.xpdustry.nohorny.client.NoHornySetting;
import java.net.URI;
import mindustry.mod.Plugin;

public final class MyPlugin extends Plugin {

    @Override
    public void init() {
        NoHornySetting.API_ENDPOINT.set(URI.create("https://localhost:8080"));
        NoHornySetting.API_AUTH_TYPE.set(NoHornyClientAuthType.BEARER);
        NoHornySetting.API_AUTH_VALUE.set("my-token");
        NoHornySetting.AUTOMOD_POLICY.set(AutoModeratorPolicy.DELETE_WARN);
    }
}
```

## Server

### Installation

This is a standalone Java application requiring:

- Java 25

Then, you can simply run `java -jar nohorny-server.jar start`.

### Configuration

See [`nohorny-server/src/main/resources/application.yaml`](nohorny-server/src/main/resources/application.yaml).

Then, you can configure the server using:

- An `application.yaml` file

```yaml
# application.yaml
server:
  port: 9090
```

- Env variables

```text
SERVER_PORT=9090 java -jar nohorny-server.jar start
```

- Or jvm properties

```text
java -jar nohorny-server.jar start -- --server.port=9090
```

## Building

- `./gradlew shadowJar` to compile all modules into jars at `nohorny-client/build/libs/nohorny-client.jar` and `nohorny-server/build/libs/nohorny-server.jar`.

- `./gradlew runMindustryServer` to run the client plugin in a local Mindustry server.

- `./gradlew spotlessApply` to apply the code formatting and the license header.

- `./gradlew :nohorny-client:jmh` to run the [benchmarks](nohorny-client/src/jmh/java/com/xpdustry/nohorny/client),
  use `-Pjmh="<args>"` to pass arguments to JMH, such as `-Pjmh="-p size=100 Tick"`.

## Performance

NoHorny does all the heavy work (rendering and classification) off the main thread,
and bounds how much tracking and grouping it does per tick, so its impact on the main loop stays minimal.

The benchmarks run on the worst possible map: entirely covered by canvases, logic displays with processors
drawing on them, and sorter and illuminator art linked to processors, with random pieces of art changing each tick.
The classifier is also answering instantly, so NoHorny never stops grouping.

Mean main loop cost of a tick, with the 16.67ms budget of a 60 TPS server in mind
(Xeon E5-1650 v4, Java 25):

| Map                         | Changes per tick | Mindustry | Mindustry + NoHorny | NoHorny overhead |
|-----------------------------|-----------------:|----------:|--------------------:|-----------------:|
| 100x100 (6k buildings)      |                0 |     ~0 µs |              3.4 µs |          +3.4 µs |
| 250x250 (38k buildings)     |                0 |     ~0 µs |             27.7 µs |         +27.7 µs |
| 500x500 (150k buildings)    |                0 |     ~0 µs |             29.8 µs |         +29.8 µs |
| 100x100                     |               10 |    425 µs |              615 µs |          +190 µs |
| 250x250                     |               10 |    486 µs |              736 µs |          +250 µs |
| 500x500                     |               10 |    484 µs |              786 µs |          +302 µs |
| 100x100                     |              100 |   4.71 ms |             5.25 ms |          +0.54 ms |
| 250x250                     |              100 |   5.02 ms |             5.68 ms |          +0.66 ms |
| 500x500                     |              100 |   4.91 ms |             6.01 ms |          +1.10 ms |

Once built, art costs at most 0.2% of the tick budget to watch, regardless of the map size.
And even when players change 6000 pieces of art per second, NoHorny stays under 7% of the tick budget.
Indexing the whole map when it loads takes a one-off 6ms, 39ms and 158ms respectively.

## Support

Need a helping hand? You can talk to the maintainers in [our discord server](https://discord.xpdustry.com)
in the `#support` channel.
