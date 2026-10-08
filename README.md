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
It can delete the buildings, refund their cost, ban the offending players, and send your moderators a Discord alert.

Enjoy this family-friendly factory building game as the [cat](https://github.com/Anuken) intended it to be.

[Install](#install-the-plugin) · [Configure](#configure-the-plugin) · [How it works](#how-it-works) · [Self-host](#host-the-classification-server) · [Develop](#develop) · [Performance](#performance) · [Support](#support)

## Install the plugin

Use Mindustry build **159 or later** and run the server with **Java 25 or later**.

1. Download `nohorny-plugin.jar` from the [latest release](https://github.com/xpdustry/nohorny/releases/latest).
2. Put the JAR in your Mindustry server's `config/mods` directory.
3. Start or restart the Mindustry server.

Look for `NoHorny successfully initialized` and a message that the API is operational in the server log.
When upgrading from an older release, remove `nohorny-client.jar` before installing the new JAR.

The plugin uses **Xpdustry's public API** by default, so you don't need to host a separate server or configure an account.
It sends rendered images of the art to the API. See the [public server's privacy page](https://nohorny.xpdustry.com/privacy) for data retention.
You can also [host your own API](#host-the-classification-server).

The default policy, **`BAN_NSFW`**, deletes art rated `NSFW` and bans the author by UUID and IP when NoHorny can identify one.
If you'd rather handle bans yourself, run this in the Mindustry server console:

```text
config nohorny-auto-mod-policy DELETE_NSFW
```

## Configure the plugin

Change settings in the Mindustry server console with `config <key> <value>`.
Mindustry saves the settings, and changes take effect without restarting the server.
To read a setting, omit the value. To restore its default, use `default` as the value.

### Choose a moderation policy

The API gives each image a `SAFE`, `WARN`, or `NSFW` rating.
`WARN` means the classifier's score falls between the server's warning and NSFW thresholds.
Choose what the plugin does with those ratings:

```text
config nohorny-auto-mod-policy <policy>
```

| Policy | Action |
| --- | --- |
| `BAN_NSFW` | Delete `NSFW` art and ban the author if known. This is the default. |
| `DELETE_NSFW` | Delete `NSFW` art. |
| `DELETE_WARN` | Delete `WARN` and `NSFW` art. |
| `DISABLED` | Keep classification and alerts, but leave moderation to you. |

Deletion removes the **whole classified group**, including linked processors for logic displays.
NoHorny refunds the deleted buildings' item costs to their teams, except in infinite-resource modes.

Classifiers can make mistakes, and the author is inferred from who built or configured the art.
To review results before enabling automatic deletion or bans, use `DISABLED` with [Discord alerts](#send-discord-alerts).

<details>
<summary>All plugin settings</summary>

| Key | Value | Default |
| --- | --- | --- |
| `nohorny-auto-mod-policy` | One of the policies above. | `BAN_NSFW` |
| `nohorny-api-endpoint` | Classification API URL, including `/api`. | `https://nohorny.xpdustry.com/api` |
| `nohorny-api-auth-type` | `DISABLED`, `BASIC`, or `BEARER`. | `DISABLED` |
| `nohorny-api-auth-value` | `username:password` for `BASIC`, or the raw token for `BEARER`. | `null` |
| `nohorny-discord-webhook` | Discord webhook URL for `WARN` and `NSFW` alerts. | `null` |
| `nohorny-discord-webhook-name` | Override the webhook's username. | `null` |
| `nohorny-discord-webhook-proxy` | Use a proxy for Discord requests. | `false` |
| `nohorny-discord-webhook-image` | `AUTO`, `ALWAYS`, or `NEVER`. | `AUTO` |
| `nohorny-debug-tap` | Let admins double-tap tracked art to inspect it. | `false` |

`null` means unset. For example, `config nohorny-discord-webhook null` stops Discord alerts.
Setting `nohorny-api-endpoint` to `null` stops classification.

</details>

### Send Discord alerts

Create a Discord webhook in the channel your moderators use, then set its URL:

```text
config nohorny-discord-webhook https://discord.com/api/webhooks/999999/abcdefgh
```

Alerts include the rating, location, and author when known.
If the API provides a request page URL, the alert has a **View request** button for inspecting the result.
Alerts work with every moderation policy, including `DISABLED`.

<details>
<summary>Example Discord alert</summary>

![NoHorny alert with a View request button](.github/discord-example.png)

</details>

Choose when to attach the image with `config nohorny-discord-webhook-image <policy>`:

| Policy | Image upload |
| --- | --- |
| `AUTO` | Attach the image only when there is no request page URL. This is the default. |
| `ALWAYS` | Attach the image and include the request page link if available. |
| `NEVER` | Include the request page link if available, without attaching the image. |

If your host cannot reach Discord, `config nohorny-discord-webhook-proxy true` enables a public proxy from [ProxyScrape](https://proxyscrape.com/free-proxy-list).
For automatic deletion of old alerts, see [MAD](https://github.com/phinner/mad).

### Inspect tracked art

Enable `config nohorny-debug-tap true`, then double-tap a tracked display, canvas, sorter, or illuminator as an in-game admin.
NoHorny labels the group and saves its rendered image in `config/mods/nohorny/debug/`.
This lets you see which buildings NoHorny groups together and what image the classifier receives.

## How it works

The Mindustry plugin tracks building placement and configuration changes, then groups nearby art into images.
Logic displays are rendered from their processors' draw instructions, canvases from their pixel data, and sorter or illuminator art from the blocks' colors.
NoHorny renders these images on the server without taking screenshots of the game.

After a short wait for construction to finish, the plugin renders a group and sends it as a JPEG to the classification API.
The API returns a rating and score. The plugin then fires a [`ClassificationEvent`](nohorny-plugin/src/main/java/com/xpdustry/nohorny/client/ClassificationEvent.java), which the moderator and Discord alerts use.
Rendering and API requests run on background threads. The event fires on **Mindustry's main thread**, where another plugin can handle the result.

The wait and the API request mean moderation takes time. Art can be visible before NoHorny removes it.
Sorter and illuminator groups are ignored unless they span at least 8 tiles in both dimensions.

The code is split into these modules:

| Module | Responsibility |
| --- | --- |
| [`nohorny-plugin`](nohorny-plugin) | Track buildings, render art, call the API, and moderate the game. |
| [`nohorny-common`](nohorny-common) | Shared image types, geometry, and API response types. |
| [`nohorny-server`](nohorny-server) | Classification API, accounts, rate limits, storage, and request history. |
| [`nohorny-native`](nohorny-native) | Run ONNX models with OpenCV, called through Java's Foreign Function and Memory API. |
| [`nohorny-frontend`](nohorny-frontend) | Solid 2 and Tailwind CSS app for statistics, request pages, and the admin panel. |

For the tracking code, start with [`NoHornyEventBus`](nohorny-plugin/src/main/java/com/xpdustry/nohorny/client/NoHornyEventBus.java) and [`GroupCollector`](nohorny-plugin/src/main/java/com/xpdustry/nohorny/client/GroupCollector.java).
For classification, start with [`ClassificationService`](nohorny-server/src/main/java/com/xpdustry/nohorny/server/ClassificationService.java).

## Host the classification server

Self-hosting lets you choose the classifier, manage API accounts, and control data retention.
The standalone server includes the API, request pages, statistics, and an admin panel.

### Start the server

Use Java 25 or later.
The release JAR includes native libraries for Linux on x86_64 and ARM64, Windows on x86_64, and macOS on ARM64.

1. Download `nohorny-server.jar` from the [latest release](https://github.com/xpdustry/nohorny/releases/latest).
2. Run `java -jar nohorny-server.jar`.
3. Open <http://127.0.0.1:8080> to see the classification counters.
4. If Mindustry runs on the same machine, set the plugin's endpoint in its server console:

   ```text
   config nohorny-api-endpoint http://127.0.0.1:8080/api
   ```

The API listens on `127.0.0.1:8080` by default.
For a remote plugin, put it behind a reverse proxy and use an endpoint such as `https://nohorny.example.com/api`.
**Keep `/api` at the end** of the plugin's endpoint.

The default ViT classifier downloads its ONNX model from Hugging Face on the first start and caches it in `.cached-models`.
Allow that download to finish before connecting the plugin.

<details>
<summary>Run with Docker</summary>

The published Docker image runs on Linux x86_64. Start it with:

```sh
docker run -d --name nohorny-server \
	-p 127.0.0.1:8080:8080 \
	-e SERVER_ADDRESS=0.0.0.0 \
	-v nohorny-data:/data \
	ghcr.io/xpdustry/nohorny-server:4.0.0-beta.11
```

`SERVER_ADDRESS` lets the app accept traffic through Docker's port mapping.
The mapping above keeps the port accessible only from the host.
The `/data` volume keeps the database and images when you replace the container.

</details>

### Configure the server

See [`application.yaml`](nohorny-server/src/main/resources/application.yaml) for all defaults.
Override settings with an `application.yaml` file in the working directory, environment variables, or JVM properties.
For example, `SERVER_PORT=9090 java -jar nohorny-server.jar` changes the port.

Set `nohorny.public-url` to the public address, such as `https://nohorny.example.com`, to include request page links in API responses and Discord alerts.
Use the address **without `/api`** here.

<details>
<summary>Choose a classifier</summary>

`nohorny.classifier.type` accepts `vit`, `sight-engine`, or an ordered list such as `[vit, sight-engine]`.
With a list, the next classifier runs only if the previous one rates the image `NSFW`.
This lets the local model filter out safe images before you pay for a SightEngine request.

The default ViT model rates scores below `0.6` as `SAFE`, scores from `0.6` to below `0.9` as `WARN`, and scores of `0.9` or more as `NSFW`.
Adjust `thresholds.warn` and `thresholds.nsfw` under `nohorny.classifier.vit` to change those boundaries.

For another ONNX model, change the Hugging Face repository, revision, and file, or set `nohorny.classifier.vit.source.type` to `local` and provide `local.path`.
The configured `labels` must match the model's output order, and `nsfw-label` must name its NSFW label.
See [`download-and-convert-model.sh`](nohorny-server/download-and-convert-model.sh) for conversion instructions.

To use SightEngine, set `SIGHT_ENGINE_USER` and `SIGHT_ENGINE_SECRET` and include `sight-engine` in the classifier list.

</details>

<details>
<summary>Create admin and API accounts</summary>

Set `NOHORNY_SECURITY_ADMIN_PASSWORD` before starting the server to create the initial admin account.
Its username is `admin`, or the value of `NOHORNY_SECURITY_ADMIN_USERNAME`.
Without a password, the server creates no initial admin.

Sign in at `/admin` and open **Users** to create accounts and manage their passwords, admin access, and rate limits.
Accounts without admin access can still authenticate to the classification API.
The configured initial admin's password and admin role are restored on each start, so change its password in the server configuration.

To connect the plugin with an API account, run these commands in the Mindustry server console:

```text
config nohorny-api-auth-value username:password
config nohorny-api-auth-type BASIC
```

The standalone server uses *HTTP Basic authentication*.
The plugin's `BEARER` option is for other APIs that accept tokens.

</details>

<details>
<summary>Request pages, storage, and retention</summary>

| Path | Purpose |
| --- | --- |
| `/` | Live classification counters. |
| `/requests/{id}` | Result details and an image that stays blurred until revealed. |
| `/privacy` | Data collection and retention information. |
| `/admin` | Request review and account management for admins. |
| `/api` | Base path for the API. |

Request pages are public to **anyone with the link**, including the option to purge the image.
The server stores submitted images for every rating, including `SAFE`.

| Data | Setting | Default |
| --- | --- | --- |
| Database file | `nohorny.storage.database` | `database.sqlite` |
| Image directory | `nohorny.storage.images` | `images` |
| Image retention | `nohorny.requests.image-retention` | 14 days |
| Request retention | `nohorny.requests.retention` | 90 days |

File paths are relative to the working directory. The Docker image stores the database and images under `/data`.
All-time classification counters remain after images and requests expire.

</details>

<details>
<summary>Rate limits and reverse proxies</summary>

The API's default limits are configured under `nohorny.rate-limit`:

| Caller | Requests per minute | Shared by | Setting |
| --- | ---: | --- | --- |
| Anonymous client | 5 | One client address | `anonymous` |
| Listed Mindustry server without an account | 60 | Servers in the same listed network | `mindustry` |
| Account | 120 for new accounts | All addresses that use the account | `user` |

`user` sets the default for new accounts. Change an existing account's limit in the admin panel.
Localhost uses the anonymous limit unless it authenticates with an account.
Set `anonymous` or `mindustry` to `0` to require an account for those callers.
When rate-limited, the plugin waits and retries, up to three attempts per classification.

For a reverse proxy, forward the whole host, including the pages and `/api`, and set:

```yaml
server:
  forward-headers-strategy: framework
```

The proxy must overwrite `Forwarded` and `X-Forwarded-*` headers so the server sees the client's address for rate limits.
Keep the default `none` if clients connect directly.

</details>

## Develop

### Build and run locally

Use a JDK 25 or later.
**The plugin build needs only the JDK.** To build the server, also install Node.js 22.12 or later, [pnpm 12](https://pnpm.io), CMake, and a C++20 compiler.

Run these commands from the repository root:

| Task | Command | Output |
| --- | --- | --- |
| Build the plugin | `./gradlew :nohorny-plugin:shadowJar` | `nohorny-plugin/build/libs/nohorny-plugin.jar` |
| Build the server | `./gradlew :nohorny-server:bootJar` | `nohorny-server/build/libs/nohorny-server.jar` |
| Run Mindustry with the plugin | `./gradlew :nohorny-plugin:runMindustryServer` | Local sandbox server. |
| Build the native classifier | `./gradlew :nohorny-native:cmakeBuild` | Native OpenCV library for your platform. |
| Check the frontend | `./gradlew :nohorny-frontend:check` | Type and lint checks. |
| Format Java and Gradle files | `./gradlew spotlessApply` | Formatted code and license headers. |

The server build compiles and bundles the native classifier and frontend.
The first native build takes longer because it also compiles OpenCV.
Run the resulting server JAR with `java -jar nohorny-server/build/libs/nohorny-server.jar`.

For frontend work, start the Java server at `127.0.0.1:8080`, then run:

```sh
cd nohorny-frontend
pnpm install
pnpm dev
```

Vite proxies API and login requests to the Java server.
Set `NOHORNY_BACKEND` to use a different backend address.

To build the Docker image locally, run `docker build -t nohorny-server .`.
The build handles Java, native libraries, and the frontend in separate stages.

### Integrate another Mindustry plugin

Add these dependencies to your `build.gradle.kts`, using the version of NoHorny installed on the server:

```kotlin
repositories {
	maven { url = uri("https://maven.xpdustry.com/releases") }
}

dependencies {
	compileOnly("com.xpdustry:nohorny-common:4.0.0-beta.11")
	compileOnly("com.xpdustry:nohorny-plugin:4.0.0-beta.11")
}
```

Declare `"dependencies": ["nohorny"]` in your plugin's `plugin.json` or `mod.json` so Mindustry loads NoHorny as a dependency.
**Install NoHorny alongside your plugin**, and keep its classes out of your plugin JAR.
Up to `4.0.0-beta.10`, the artifact was named `nohorny-client`. The Java package remains `com.xpdustry.nohorny.client`.

Subscribe to `ClassificationEvent` to receive the classified group, nullable author, and [`ClassificationResponse`](nohorny-common/src/main/java/com/xpdustry/nohorny/common/ClassificationResponse.java):

```java
import arc.Events;
import arc.util.Log;
import com.xpdustry.nohorny.client.ClassificationEvent;
import mindustry.mod.Plugin;

public final class MyPlugin extends Plugin {
	@Override
	public void init() {
		Events.on(ClassificationEvent.class, event -> {
			Log.info("Art at (@, @) was rated @",
				event.group().x(), event.group().y(), event.response().rating());
		});
	}
}
```

The handler runs on Mindustry's main thread. `event.author()` is `null` when NoHorny cannot identify an author.
The response includes the classifier name, rating, score, request identifier, and optional request page URL.

Use [`NoHornySetting`](nohorny-plugin/src/main/java/com/xpdustry/nohorny/client/NoHornySetting.java) to change the same settings available in the console.
For custom moderation, call `NoHornySetting.AUTO_MOD_POLICY.set(AutoModeratorPolicy.DISABLED)` in `init()` to disable the built-in moderator.
Import `com.xpdustry.nohorny.client.NoHornySetting` and `com.xpdustry.nohorny.client.AutoModeratorPolicy` for that call.

### Call the API directly

You can use the classification API without Mindustry. Send the JPEG bytes as the request body:

```sh
curl --fail-with-body \
	-H 'Content-Type: image/jpeg' \
	--data-binary @art.jpg \
	http://127.0.0.1:8080/api/classify
```

Add `--user username:password` if you use an API account.
The JSON response has the fields in `ClassificationResponse`. `url` is optional and requires the server's `nohorny.public-url` setting.
Errors return a JSON `message`. A rate-limited request returns HTTP `429` with a `Retry-After` header in seconds.
For a health check, use `GET /api/status`.

## Performance

The benchmark puts NoHorny through a **deliberate worst-case load**: maps packed with art and up to **100 art changes every tick**.
The largest map is 500×500 tiles with about 150,000 buildings.
At 60 TPS, 100 changes per tick means **6,000 art changes a second**, continuously.

The benchmark accepts every group instantly, so the trackers keep grouping art without waiting for the API.

Even under these conditions, NoHorny stays light on the benchmark machine:

- With no art changes, mean tracking overhead is below **0.1 µs per tick**, even on the map packed with 150,000 buildings.
- At 10 changes per tick, the highest mean overhead is **0.18 ms**, about **1.1%** of a tick's 16.7 ms budget at 60 TPS.
- At 100 changes per tick, the highest mean overhead is **1.29 ms**, still **under 8% of a tick** across every tested map and garbage collector.
- On the largest map, tracking adds **50 to 53 MiB** of heap, about **15%** on top of the map's own memory use.

At 100 changes per tick, Mindustry itself spends roughly **4 to 8 times as much** applying the changes as NoHorny spends tracking them.
G1 and Serial give comparable results.

These measurements cover tracking and grouping on Mindustry's main thread.
Image rendering and API requests run on background threads.

<details>
<summary>Benchmark conditions and results</summary>

The recorded run used Xpdustry's Xeon E5-1650 v4 server, Java 25, a 2 GiB heap, and G1 and Serial garbage collectors.
The 100×100, 250×250, and 500×500 maps were filled with canvases, logic displays, sorters, and illuminators.
Each large display had six processors with 100 draw instructions each.
Each tick applied 0, 10, or 100 random art changes.

The table shows mean main-thread time per tick.
The Mindustry columns measure the game's own cost to apply the changes. The NoHorny columns show the extra cost of tracking them.

| Map | Art changes per tick | Mindustry, G1 | NoHorny, G1 | Mindustry, Serial | NoHorny, Serial |
| --- | ---: | ---: | ---: | ---: | ---: |
| 100×100, 6k buildings | 0 | ~0 µs | +0.05 µs | ~0 µs | +0.04 µs |
| 250×250, 38k buildings | 0 | ~0 µs | +0.05 µs | ~0 µs | +0.04 µs |
| 500×500, 150k buildings | 0 | ~0 µs | +0.05 µs | ~0 µs | +0.04 µs |
| 100×100 | 10 | 422 µs | +102 µs | 405 µs | +126 µs |
| 250×250 | 10 | 458 µs | +172 µs | 465 µs | +173 µs |
| 500×500 | 10 | 459 µs | +143 µs | 523 µs | +178 µs |
| 100×100 | 100 | 4.27 ms | +0.76 ms | 4.13 ms | +0.55 ms |
| 250×250 | 100 | 4.48 ms | +0.87 ms | 4.83 ms | +1.29 ms |
| 500×500 | 100 | 4.67 ms | +1.14 ms | 5.37 ms | +1.01 ms |

The next table shows the initial map scan.
Heap use was measured after a full garbage collection in the run with 100 changes per tick.

| Map | Scan, G1 | Scan, Serial | Map heap | NoHorny heap |
| --- | ---: | ---: | ---: | ---: |
| 100×100 | 4.6 ms | 2.8 ms | 25 MiB | +2.2 MiB |
| 250×250 | 32 ms | 22 ms | 94 MiB | +12.7 MiB |
| 500×500 | 127 ms | 110 ms | 340 MiB | +50 to 53 MiB |

</details>

To reproduce the measurements, run the [JMH benchmarks](nohorny-plugin/src/jmh/java/com/xpdustry/nohorny/client):

```sh
./gradlew :nohorny-plugin:jmhFull
```

This uses 3 forks with G1 and Serial and takes about an hour.
For a shorter run with one fork and G1, use `./gradlew :nohorny-plugin:jmh`, which takes about 10 minutes.
Results are written to `nohorny-plugin/build/jmh/jmhFull.json` or `jmh.json`.

## Support

For help with setup or unexpected results, ask in `#support` on the [Xpdustry Discord server](https://discord.xpdustry.com).
For a bug report, include your Mindustry and NoHorny versions, relevant logs, and a request identifier or link if you have one.

NoHorny is available under the [MIT license](LICENSE.md).
