# NoHorny

<p align="center">
  <img src=".github/nohorny.svg" alt="NoHorny logo" width="388">
</p>

[![Release](https://img.shields.io/github/v/release/xpdustry/nohorny?include_prereleases&color=008080)](https://github.com/xpdustry/nohorny/releases)
[![Downloads](https://img.shields.io/github/downloads/xpdustry/nohorny/total?color=008080)](https://github.com/xpdustry/nohorny/releases)
[![Mindustry 8.0](https://img.shields.io/badge/Mindustry-8.0-008080)](https://github.com/Anuken/Mindustry/releases)
[![Discord](https://img.shields.io/discord/519293558599974912?color=008080&label=Discord)](https://discord.xpdustry.com)

Are you sick of players turning your awesome Mindustry server into an NSFW gallery?
Do you want your logic displays back without the fear of seeing anime girls in questionable positions?

Introducing **NoHorny**, your automatic NSFW moderation plugin.
It detects unsafe logic displays, canvases, and pixel art made with sorters or illuminators.
It can delete the buildings, refund their cost, and ban the offending players.

Enjoy this family-friendly factory building game as the [cat](https://github.com/Anuken) intended it to be.

The plugin uses the Xpdustry classification API by default. You can also [host your own](#host-the-classification-server).

[Install](#install-the-plugin) · [Configure](#configure-the-plugin) · [Self-host](#host-the-classification-server) · [Develop](#develop) · [Performance](#performance) · [Support](#support)

## Install the plugin

Use Mindustry build **159 or later** and **Java 25 or later**.

1. Download the plugin JAR from the [latest release](https://github.com/xpdustry/nohorny/releases/latest).
   The filename is `nohorny-plugin.jar` or, for older releases, `nohorny-client.jar`.
2. Put the JAR file in your Mindustry server's `config/mods` directory.
3. Start the Mindustry server.

The default policy is `BAN_NSFW`. When the API rates art as `NSFW`, NoHorny deletes the buildings and refunds their cost to the team.
It also bans the author if it can identify one.
To delete the buildings without a ban, run this command in the server console:

```text
config nohorny-auto-mod-policy DELETE_NSFW
```

[SLF4MD](https://github.com/xpdustry/slf4md) is an optional logging plugin.

## Configure the plugin

Use the Mindustry server console to change a setting:

```text
config <key> <value>
```

Choose a moderation policy with `config nohorny-auto-mod-policy <policy>`.

| Policy | Action |
| --- | --- |
| `BAN_NSFW` | Delete `NSFW` buildings and ban the author if known. This is the default. |
| `DELETE_NSFW` | Delete `NSFW` buildings. |
| `DELETE_WARN` | Delete `WARN` and `NSFW` buildings. |
| `DISABLED` | Take no automatic moderation action. Classification continues. |

Every delete policy refunds the cost of the deleted buildings to their team.

<details>
<summary>All plugin settings</summary>

| Key | Value | Default |
| --- | --- | --- |
| `nohorny-auto-mod-policy` | One of the moderation policies above. | `BAN_NSFW` |
| `nohorny-api-endpoint` | Classification API URL, including `/api`. | `https://nohorny.xpdustry.com/api` |
| `nohorny-api-auth-type` | `DISABLED`, `BASIC`, or `BEARER`. | `DISABLED` |
| `nohorny-api-auth-value` | `username:password` for `BASIC`, or the raw token for `BEARER`. | Empty |
| `nohorny-discord-webhook` | Discord webhook URL for alerts about unsafe art. | Empty |
| `nohorny-discord-webhook-name` | Override the webhook's username. | Empty |
| `nohorny-discord-webhook-proxy` | Use a proxy for Discord requests. | `false` |
| `nohorny-discord-webhook-image` | `AUTO`, `ALWAYS`, or `NEVER`. See the Discord section below. | `AUTO` |
| `nohorny-debug-tap` | Let admins double-tap tracked art to inspect it. | `false` |

</details>

<details>
<summary>Discord alerts and image uploads</summary>

Create a Discord webhook for the channel that receives alerts.
Set its URL in the Mindustry server console:

```text
config nohorny-discord-webhook https://discord.com/api/webhooks/999999/abcdefgh
```

When the API returns a request page URL, the alert includes a **View request** button.

![NoHorny alert with a View request button](.github/discord-example.png)

Choose when Discord receives the image with `config nohorny-discord-webhook-image <policy>`.

| Policy | Image upload |
| --- | --- |
| `AUTO` | Upload only if the API returns no request page URL. This is the default. |
| `ALWAYS` | Upload the image. Include the request page link if available. |
| `NEVER` | Do not upload the image. Include the request page link if available. |

If your host cannot access Discord, enable the proxy option:

```text
config nohorny-discord-webhook-proxy true
```

NoHorny finds a proxy through [ProxyScrape](https://proxyscrape.com/free-proxy-list).
For automatic deletion of old alerts, see [MAD](https://github.com/phinner/mad).

</details>

<details>
<summary>Inspect tracked art</summary>

Enable debug mode in the Mindustry server console:

```text
config nohorny-debug-tap true
```

As an admin, double-tap a tracked display, canvas, sorter, or illuminator group.
NoHorny labels the group in the game.
It also saves an image of the group in `config/mods/nohorny/debug/`.

</details>

## Host the classification server

The standalone server provides the classification API, request pages, and an admin panel.
Self-hosting lets you choose the classifier, manage API accounts, and control data retention.

<details>
<summary>Start the server and connect the plugin</summary>

Use Java 25 or later.

1. Download `nohorny-server.jar` from the [latest release](https://github.com/xpdustry/nohorny/releases/latest).
2. Run the server:

   ```sh
   java -jar nohorny-server.jar
   ```

3. Open <http://127.0.0.1:8080> to view the classification counters.
4. If the plugin runs on the same machine, set its API endpoint in the Mindustry server console:

   ```text
   config nohorny-api-endpoint http://127.0.0.1:8080/api
   ```

The server listens on `127.0.0.1:8080` by default.
For a remote plugin, use a reverse proxy and set the endpoint to `https://nohorny.example.com/api`.
Include `/api` in the endpoint.

The default ViT classifier downloads its ONNX model from Hugging Face on the first start.
It keeps the model in `.cached-models` for later starts.
For classifier options and model settings, see [`application.yaml`](nohorny-server/src/main/resources/application.yaml).

</details>

<details>
<summary>Server configuration and storage</summary>

The [default configuration](nohorny-server/src/main/resources/application.yaml) lists the server settings and classifier options.
You can override settings with an `application.yaml` file in the working directory, environment variables, or JVM properties.

To change the port with a configuration file, use:

```yaml
server:
  port: 9090
```

To change the port with an environment variable, run:

```sh
SERVER_PORT=9090 java -jar nohorny-server.jar
```

To change the port with a JVM property, run:

```sh
java -Dserver.port=9090 -jar nohorny-server.jar
```

Set `nohorny.public-url` to the public server address, such as `https://nohorny.example.com`.
The API then includes request page links in its classification responses.

| Setting | Default | Contents |
| --- | --- | --- |
| `nohorny.storage.database` | `database.sqlite` | SQLite database. |
| `nohorny.storage.images` | `images` | Submitted images. |

The Docker image stores the database and images under `/data`.
Mount a volume at `/data` to keep those files when you replace the container.

</details>

<details>
<summary>Request pages and data retention</summary>

| Path | Purpose |
| --- | --- |
| `/` | Live classification counters. |
| `/requests/{id}` | Request details and an image that stays blurred until revealed. Anyone with the link can purge the image. |
| `/privacy` | Data collection and retention information. |
| `/admin` | Request filters and user management for admins. |
| `/api` | Base path for the JSON API. |

The server stores images for all ratings, including `SAFE`, for debugging.
The default retention periods are:

| Data | Setting | Retention |
| --- | --- | --- |
| Images | `nohorny.requests.image-retention` | 14 days |
| Requests | `nohorny.requests.retention` | 90 days |
| All-time counters | None | Kept after images and requests expire |

</details>

<details>
<summary>Admin accounts and API authentication</summary>

Set `NOHORNY_SECURITY_ADMIN_PASSWORD` before you start the server to create the first admin account.
The default username is `admin`.
Set `NOHORNY_SECURITY_ADMIN_USERNAME` to use another username.

You can also set the account in `application.yaml`:

```yaml
nohorny:
  security:
    admin:
      username: admin
      password: ${NOHORNY_ADMIN_PASSWORD:}
```

Set `NOHORNY_ADMIN_PASSWORD` when you use this example.
Keep the trailing colon in the placeholder so that a missing variable resolves to an empty password.
Without a password, the server creates no bootstrap admin.

On each start, the server restores the bootstrap admin's configured password and admin role.
Change that password in the configuration.

Sign in at `/admin` and open the **Users** view to create other accounts.
You can change their passwords, roles, and rate limits, or delete them.
Admins can use the admin panel. Other accounts can authenticate to the classification API.
You cannot delete or remove the admin role from the bootstrap admin or your own account.

To connect the plugin with an API account, run these commands in the Mindustry server console:

```text
config nohorny-api-auth-type BASIC
config nohorny-api-auth-value username:password
```

Use `BASIC` for accounts on the standalone server. Use `BEARER` if your API accepts tokens.

</details>

<details>
<summary>Rate limits and reverse proxies</summary>

The classification API uses these default limits under `nohorny.rate-limit`:

| Caller | Requests per minute | Shared by | Setting |
| --- | ---: | --- | --- |
| Anonymous client | 5 | One client address | `anonymous` |
| Listed Mindustry server without an account | 60 | The servers in one network | `mindustry` |
| Account with HTTP Basic authentication | 120 | All addresses that use the account | `user`, then the account's own limit |

Set `anonymous` or `mindustry` to `0` to require an account for those callers.
The `user` setting supplies the default limit for new accounts.
Change an existing account's limit in the admin panel.

For a reverse proxy, forward the whole host to the server, including the pages and `/api`.
Enable forwarded headers in `application.yaml`:

```yaml
server:
  forward-headers-strategy: framework
```

The proxy must overwrite the `Forwarded` and `X-Forwarded-*` headers.
Keep `forward-headers-strategy: none` if clients connect to the server directly.

</details>

## Develop

The repository contains the Mindustry plugin, a shared Java library, the native classifier, the Java server, and its web frontend.

<details>
<summary>Build and run locally</summary>

Use a JDK 25 or later.
To build the server, also install Node.js 22 or later, [pnpm 12](https://pnpm.io), CMake, and a C++20 compiler.

Run these commands from the repository root:

| Task | Command | Result |
| --- | --- | --- |
| Build the plugin | `./gradlew :nohorny-plugin:shadowJar` | `nohorny-plugin/build/libs/nohorny-plugin.jar` |
| Build the server | `./gradlew :nohorny-server:bootJar` | `nohorny-server/build/libs/nohorny-server.jar` |
| Run the plugin locally | `./gradlew runMindustryServer` | A local Mindustry server with the plugin. |
| Build the native classifier | `./gradlew :nohorny-native:cmakeBuild` | Native OpenCV classifier library. |
| Check the frontend | `./gradlew :nohorny-frontend:check` | Type checks and lint checks. |
| Format the code | `./gradlew spotlessApply` | Code formatting and license headers. |

The server build also builds the native classifier and the frontend, then bundles them in the JAR file.
The first native build takes longer because it compiles OpenCV.
If CMake is absent, Gradle skips the native build. The default ViT classifier then cannot run.

The [`nohorny-frontend`](nohorny-frontend) app uses Solid 2 and Tailwind CSS.
To work on the frontend, start the Java server at `127.0.0.1:8080`.
Then run:

```sh
cd nohorny-frontend
pnpm install
pnpm dev --host 127.0.0.1
```

The development server proxies API requests to the Java server.
Set `NOHORNY_BACKEND` to use another backend address.

To build the Docker image, run:

```sh
docker build -t nohorny-server .
```

The Docker build compiles the native classifier and frontend in separate stages.
To use native libraries from a directory in the build context, pass `--build-arg PREBUILT_NATIVES=<dir>`.

</details>

<details>
<summary>Integrate another Mindustry plugin</summary>

Add the NoHorny API dependencies to your `build.gradle`.
Replace `VERSION` with a [published version](https://maven.xpdustry.com/#/releases/com/xpdustry/nohorny-plugin).

```gradle
repositories {
  maven { url = uri("https://maven.xpdustry.com/releases") }
}

dependencies {
  compileOnly("com.xpdustry:nohorny-common:VERSION")
  compileOnly("com.xpdustry:nohorny-plugin:VERSION")
}
```

For older releases, use `com.xpdustry:nohorny-client:VERSION` instead of `com.xpdustry:nohorny-plugin:VERSION`.

Subscribe to [`ClassificationEvent`](nohorny-plugin/src/main/java/com/xpdustry/nohorny/client/ClassificationEvent.java) to handle classification results:

```java
import arc.Events;
import com.xpdustry.nohorny.client.ClassificationEvent;
import mindustry.mod.Plugin;

public final class MyPlugin extends Plugin {
  @Override
  public void init() {
    Events.on(ClassificationEvent.class, event -> {
      System.out.println("Classified group at " + event.group().x() + ", " + event.group().y());
    });
  }
}
```

Use [`NoHornySetting`](nohorny-plugin/src/main/java/com/xpdustry/nohorny/client/NoHornySetting.java) to set plugin options from Java:

```java
import com.xpdustry.nohorny.client.AutoModeratorPolicy;
import com.xpdustry.nohorny.client.NoHornyClientAuthType;
import com.xpdustry.nohorny.client.NoHornySetting;
import java.net.URI;
import mindustry.mod.Plugin;

public final class MyPlugin extends Plugin {
  @Override
  public void init() {
    NoHornySetting.API_ENDPOINT.set(URI.create("http://127.0.0.1:8080/api"));
    NoHornySetting.API_AUTH_TYPE.set(NoHornyClientAuthType.BASIC);
    NoHornySetting.API_AUTH_VALUE.set("username:password");
    NoHornySetting.AUTO_MOD_POLICY.set(AutoModeratorPolicy.DELETE_WARN);
  }
}
```

For custom moderation, set `AUTO_MOD_POLICY` to `DISABLED` to stop the built-in moderator from acting on the same events.

</details>

<details>
<summary>Publish a release</summary>

Release assets are immutable after publication.

1. Run the [Draft Release workflow](https://github.com/xpdustry/nohorny/actions/workflows/draft-release.yaml) on the commit to release.
2. Edit the draft's release notes.
3. Publish the draft.

The workflow creates a draft pinned to the selected commit, with the JAR files attached and the commit list as initial notes.
Publication triggers Maven and Docker publishing, then a version bump in `build.gradle.kts`.

</details>

## Performance

NoHorny renders and classifies art on separate threads.
The Mindustry main loop tracks changes to the buildings.
In the recorded benchmark, this tracking added about 0.3 ms per tick at 10 art changes per tick on a 500×500 map.

<details>
<summary>Benchmark results and reproduction</summary>

The recorded measurements used Xpdustry's server hardware, a Xeon E5-1650 v4 with Java 25.
The maps were covered with canvases, logic displays, sorters, and illuminators.
Players changed random art each tick, and the classifier returned immediately.
This setup measures tracking cost under sustained changes. It does not measure real classification latency.

The table shows the mean main loop cost per tick. At 60 TPS, one tick has about 16.7 ms available.
The **Without NoHorny** column measures Mindustry's own cost to apply the art changes.

| Map | Art changes per tick | Without NoHorny | With NoHorny | Added cost |
| --- | ---: | ---: | ---: | ---: |
| 100×100, 6k buildings | 0 | ~0 µs | 0.07 µs | +0.04 µs |
| 250×250, 38k buildings | 0 | ~0 µs | 0.07 µs | +0.04 µs |
| 500×500, 150k buildings | 0 | ~0 µs | 0.06 µs | +0.04 µs |
| 100×100 | 10 | 401 µs | 549 µs | +148 µs |
| 250×250 | 10 | 421 µs | 681 µs | +260 µs |
| 500×500 | 10 | 425 µs | 733 µs | +308 µs |
| 100×100 | 100 | 4.05 ms | 4.58 ms | +0.53 ms |
| 250×250 | 100 | 4.27 ms | 5.62 ms | +1.35 ms |
| 500×500 | 100 | 4.36 ms | 5.41 ms | +1.05 ms |

At 10 changes per tick, the added cost stayed below 2% of the tick budget.
At 100 changes per tick, the largest added cost was 1.35 ms, below 9% of the tick budget.
The initial map scan took 5 ms, 33 ms, and 129 ms for the three map sizes in order.

Run the [JMH benchmarks](nohorny-plugin/src/jmh/java/com/xpdustry/nohorny/client) with:

```sh
./gradlew :nohorny-plugin:jmh
```

Use `-Pjmh` to pass JMH arguments. For example:

```sh
./gradlew :nohorny-plugin:jmh -Pjmh="-p size=100 Tick"
```

</details>

## Support

Ask the maintainers in the `#support` channel of the [Xpdustry Discord server](https://discord.xpdustry.com).
NoHorny is available under the [MIT license](LICENSE.md).
