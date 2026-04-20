# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/),
and this project adheres to [Semantic Versioning](http://semver.org/).

## v4.0.0-beta.2 - 2026-04-20

### Changes & New features

- Skip group if the anchor is not eligible (#66) ([`99ff1bd`](https://github.com/xpdustry/nohorny/commit/99ff1bde82bbfbcb389a6ed9460828b2bd8ccf79))
- Refactored logging to make slf4md optional (#65) ([`cf14a90`](https://github.com/xpdustry/nohorny/commit/cf14a90ef9e333ed81f1e1b5bceab3ab8366eac7))
- Better auto-mod logging (#64) ([`97d4c92`](https://github.com/xpdustry/nohorny/commit/97d4c92722bd786cefb877efb6c17896c3f25157))
- Increase group range of CanvasTracker ([`2300fe9`](https://github.com/xpdustry/nohorny/commit/2300fe9d47070915dbd0b687e008104e003d5bc7))
- Disable tileable displays support ([`c9015ec`](https://github.com/xpdustry/nohorny/commit/c9015ec8fd78f048f4d92cf913d27d9c989e39cf))

### Bugfixes

- Fix possible NPE in GroupingVirtualBuildingIndex ([`a6a4893`](https://github.com/xpdustry/nohorny/commit/a6a4893aaf748dd0d581c9bbdeb6988d9c79d35d))
- Fix nohorny-common not being bundled as "api" in client and server ([`1aa8bda`](https://github.com/xpdustry/nohorny/commit/1aa8bdab60db18aa075bebab539541077c967451))

### Maintenance

- Moved ConfigUtils#registerSafeSettingEntry to MindustryUtils ([`741d1a4`](https://github.com/xpdustry/nohorny/commit/741d1a4d4cfe56fbe454e6833011b481fd191121))
- Removed stale gson relocation ([`753ef65`](https://github.com/xpdustry/nohorny/commit/753ef65d35718249abdb3b04159b0e5aa8093745))

## v4.0.0-beta.1 - 2026-04-13

### Changes & New features

**:warning: BREAKING :warning:**

- NoHorny has been rewritten in java and is now split into `nohorny-common`, `nohorny-client`, and `nohorny-server`.
- The standalone server currently supports image classification via a ViT model.
- Replaced the old `config.yaml` flow with Mindustry config entries: `nohorny-api-endpoint`, `nohorny-automod-policy`, and `nohorny-debug-tap`.
- Added admin debug double-tap tooling to inspect tracked display/canvas groups in-game and export rendered PNG snapshots.
- Replaced `ImageAnalyzerEvent` with the new `ClassificationEvent` in `nohorny-client` for other plugins.
- Added protection against logic processor link spam when matching processors to displays.
- Simplified the tracking, allowing us to remove the old background worker thread model.
- The plugin no longer depends on `kotlin-runtime` or `sql4md`; only [`slf4md`](https://github.com/xpdustry/slf4md) remains required.

## v3.0.3 - 2025-09-25

### Changes

- NoHorny is now compatible with V8. Thanks @ZetaMap.

### Bugfixes

- Fixed H2 cache not initializing properly.
- Fixed NoHorny never properly processing processor links. Basically making the plugin useless. Sorry about that.

## v3.0.2 - 2024-12-19

### Changes

- `auto-mod` config is now more intuitive.

## v3.0.1 - 2024-12-19

### Fixes

- Fixed `ConcurrentModificationException` occurring when calling `GroupingBlockIndex#removeAll`.

## v3.0.0-beta.1 - 2024-11-15

### Features

- Removed `NoHornyAPI#setCache` in favor of a custom H2 cache.
- Added `FallbackAnalalyzer`.
- Many improvements of NoHorny internals.

### Fixes

- Solved performances issues by offloading tracking in a background thread.

## v2.2.0 - 2024-04-03

### Features

- nohorny is no longer hard dependent on [distributor](https://github.com/xpdustry/distributor).

### Bugfixes

- Fix `ImageAnalyzer.Result.EMPTY` not being really static.

### Chores

- Bumped dependencies, including kotlin to `v1.9.23`, make sure to update [kotlin-runtime](https://github.com/xpdustry/kotlin-runtime) accordingly.

## v2.1.0 - 2024-01-24

### Changes

- Misc improvements in the internals.
- Bumped kotlin version.

### Bugfixes

- Add un-relocated lib (snakeyaml).
- Fix bug where blocks on same axis spaced by 1 are in same cluster.

### Chores

- Added tests and test upload.

## v2.0.0 - 2023-12-23

### Changes

- Replaced java awt Point with immutable version.
- Misc improvements in the internals.

### Bugfixes

- Added tracking for destructed blocks and blocks changed by the server.
- nohorny autoban now set flagged blocks to air instead of destructing them (to avoid lagging players).

## v2.0.0-rc.3 - 2023-12-13

### Features

- Added https://moderatecontent.com in the available analyzers (@osp54).

## v2.0.0-rc.2 - 2023-12-12

### Bugfixes

- Fixed `NoHornyTracker` not resetting when a new map is loaded.
- Fixed forgotten `image` field in `ImageAnalyzerEvent`.

## v2.0.0-rc.1 - 2023-12-12

Initial release candidate of the next major release.
