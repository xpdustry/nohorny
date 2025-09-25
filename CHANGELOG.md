# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/),
and this project adheres to [Semantic Versioning](http://semver.org/).

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
