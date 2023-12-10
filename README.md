# nohorny

[![Xpdustry latest](https://maven.xpdustry.com/api/badge/latest/releases/com/xpdustry/nohorny?color=00FFFF&name=nohorny&prefix=v)](https://github.com/xpdustry/nohorny/releases)
[![Build status](https://github.com/xpdustry/nohorny/actions/workflows/build.yml/badge.svg?branch=master&event=push)](https://github.com/xpdustry/nohorny/actions/workflows/build.yml)
[![Mindustry 7.0 ](https://img.shields.io/badge/Mindustry-7.0-ffd37f)](https://github.com/Anuken/Mindustry/releases)

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
