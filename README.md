# NoHornyPlugin

[![Build status](https://github.com/Xpdustry/NoHornyPlugin/actions/workflows/build.yml/badge.svg?branch=master&event=push)](https://github.com/Xpdustry/NoHornyPlugin/actions/workflows/build.yml)
[![Mindustry 6.0 | 7.0 ](https://img.shields.io/badge/Mindustry-6.0%20%7C%207.0-ffd37f)](https://github.com/Anuken/Mindustry/releases)
[![Xpdustry latest](https://repo.xpdustry.fr/api/badge/latest/snapshots/fr/xpdustry/no-horny-plugin?color=00FFFF&name=NoHornyPlugin&prefix=v)](https://github.com/Xpdustry/NoHornyPlugin/releases)

## Description

**/!\ This plugin requires [Distributor](https://github.com/Xpdustry/Distributor) which can only be used in a BE server since [#6328](https://github.com/Anuken/Mindustry/pull/6328) is needed.**

Simple Mindustry plugin which aims to provide utilities to manage horny stuff in your server.

For now, it's an implementation of [L0615T1C5-216AC-9437/GlobalImageBan](https://github.com/L0615T1C5-216AC-9437/GlobalImageBan) via a service API.

To interact with it, you can either use this plugin as a dependency with your jvm project by adding the following in your `build.gradle`

```gradle
repositories {
    maven { url = uri("https://repo.xpdustry.fr/releases") }
}

dependencies {
    compileOnly("fr.xpdustry:no-horny-plugin:1.0.0")
}
```

Or call it in javascript with [distributor-js](https://github.com/Xpdustry/Distributor/tree/master/distributor-script/distributor-js).

The easiest way is to listen to the hit event such as :

```java
Events.on(HornyLogicBuildEvent.class, event -> {
  /* ... */  
})
```

A concrete example with `distributor-js` would be :

```js
importPackage(Packages.fr.xpdustry.nohorny.logic)

Events.on(HornyLogicBuildEvent, cons(e => {
	e.player().kick("Building NSFW");
}))
```

## Building

- `./gradlew jar` for a simple jar that contains only the plugin code.
- `./gradlew shadowJar` for a fatJar that contains the plugin and its dependencies (use this for your server).

## Testing

- `./gradlew runMindustryClient`: Run mindustry in desktop with the plugin.
- `./gradlew runMindustryServer`: Run mindustry in a server with the plugin.

## TODO

- [ ] Adding faster service implementations.
- [ ] Adding configurations for GIB.

