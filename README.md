# NoHornyPlugin

[![Build status](https://github.com/Xpdustry/NoHornyPlugin/actions/workflows/build.yml/badge.svg?branch=master&event=push)](https://github.com/Xpdustry/NoHornyPlugin/actions/workflows/build.yml)
[![Mindustry 7.0 ](https://img.shields.io/badge/Mindustry-7.0-ffd37f)](https://github.com/Anuken/Mindustry/releases)
[![Xpdustry latest](https://repo.xpdustry.fr/api/badge/latest/releases/fr/xpdustry/no-horny-plugin?color=00FFFF&name=NoHornyPlugin&prefix=v)](https://github.com/Xpdustry/NoHornyPlugin/releases)

## Description

A simple Mindustry plugin which aims to provide utilities to manage horny stuff in your server, especially logic NSFW.

For now, it's an implementation of [L0615T1C5-216AC-9437/GlobalImageBan](https://github.com/L0615T1C5-216AC-9437/GlobalImageBan) via a service API.

To interact with it, you can either :

- Configure it with the created config file `./distributor/plugins/xpdustry-no-horny-plugin/config.properties` :

  - `nohorny.logic-build.cache-size`: The number of logic build cached by the anti nsfw logic service.

  - `nohorny.logic-build.deep-search`: Enable/Disable the deep search feature, which means more accurate results but longer execution time. You may increase the number of threads with the `distributor.service.threads` property in Distributor config.

  - `nohorny.logic-build.default-action`: The default action to perform on a player (`NONE`, `KICK` or `BAN`) depending on your needs.

- Use this plugin as a dependency with your jvm project by adding the following :

  - `build.gradle`

    ```gradle
    repositories {
        maven { url = uri("https://repo.xpdustry.fr/releases") }
    }
    
    dependencies {
        compileOnly("fr.xpdustry:no-horny-plugin:1.1.2")
    }
    ```
    
  - `plugin.json`
    
    ```json
    {
      "dependencies": [
        "xpdustry-no-horny-plugin"
      ]
    }
    ```
    
## Running

[distributor-core](https://github.com/Xpdustry/Distributor) is required as a dependency.

If you run on V6 or V7 up to v135, you will need [mod-loader](https://github.com/Xpdustry/ModLoaderPlugin).

## Building

- `./gradlew jar` for a simple jar that contains only the plugin code.
- `./gradlew shadowJar` for a fatJar that contains the plugin and its dependencies (use this for your server).

## Testing

- `./gradlew runMindustryClient`: Run Mindustry in desktop with the plugin.
- `./gradlew runMindustryServer`: Run Mindustry in a server with the plugin.

## TODO

- [ ] Adding faster service implementations.
- [x] Adding configurations for GIB.
