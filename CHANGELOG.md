# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/),
and this project adheres to [Semantic Versioning](http://semver.org/).

## v4.0.0-beta.7 - 2026-05-22

### Changes & New features

- Added smart delays to maximize image quality, thanks to @BnDLett from MDN for the report ([`f6a36c1`](https://github.com/xpdustry/nohorny/commit/f6a36c1b833f505f790ea3364222d5a95554f00b))
- Added a docker package ([`538e305`](https://github.com/xpdustry/nohorny/commit/538e305e9c5dda84fc9a6475b55af9f34d76901d))

### Bugfixes

- Fix grouper range calculation dividing effective range by 2 ([`8f02eb3`](https://github.com/xpdustry/nohorny/commit/8f02eb30ff7c31ecf94169c1a29e892158ff7009))
- Do not fire a setting change event if the value remains the same ([`6c8a6fe`](https://github.com/xpdustry/nohorny/commit/6c8a6fedc6bc15866e167f48afa984f0e6a1ad71))
- Do not include the file name in the vit model identifier ([`14927fc`](https://github.com/xpdustry/nohorny/commit/14927fc3b894485d8310bcabd7a94f3e9ce273ad))

### Maintenance

- Vendor parts of methanol to reduce nohorny client jar size, went from 600kb to 120kb ([`242d4bf`](https://github.com/xpdustry/nohorny/commit/242d4bf791a952cdadb020b83de2829df5b0517f))

## v4.0.0-beta.6 - 2026-05-18

### Changes & New features

- Added the ability to change the username of discord webhook messages ([`75d85cd`](https://github.com/xpdustry/nohorny/commit/75d85cd425629ace2f23379f71a490f9971a58d5))
- Split author info in discord webhook message ([`0a62c55`](https://github.com/xpdustry/nohorny/commit/0a62c55e986d5d111b06e8d1578943750f8c47e5))
- Added author last name in discord webhook message ([`0a62c55`](https://github.com/xpdustry/nohorny/commit/0a62c55e986d5d111b06e8d1578943750f8c47e5))
- Refactor nohorny client settings to allow safe programmatic access (#81)
- Increased the thresholds of the default nohorny server model ([`f88e5dc`](https://github.com/xpdustry/nohorny/commit/f88e5dcdf984de99d8eba82c8fda6bcd658ce7fc))

### Bugfixes

- Fix crash on invalid nohorny server endpoint config ([`24299d8`](https://github.com/xpdustry/nohorny/commit/24299d8e6cdb92d1a2425fd5a3cbd9152573f870))
- Better trace ID in discord webhook alert footer ([`d81aa21`](https://github.com/xpdustry/nohorny/commit/d81aa21f432c876b3fdfe7aaed2b6277ec8d579c))
- Fix misleading image format in nohorny-server trace logs ([`f5e1518`](https://github.com/xpdustry/nohorny/commit/f5e151827c4a541956bcab59b841cdf3f608c5ee))
- Ceil confidence score in discord webhook message ([`4155496`](https://github.com/xpdustry/nohorny/commit/415549609320196c32e766239ff20b7a5f6110e6))

### Maintenance

- Fixed typos in `DiscordWebhook` ([`b771c7d`](https://github.com/xpdustry/nohorny/commit/b771c7d2261c0a16e82c125bcb911a7dd6c145be))
- Added Javadoc for ClassificationResponse and ClassificationEvent ([`73fc92d`](https://github.com/xpdustry/nohorny/commit/73fc92d4d2f612a69ae47069de6613d670164dd0))

## v4.0.0-beta.5 - 2026-05-11

### Changes & New features

- Better discord onboarding (#79)

### Bugfixes

- Filter out small display groups to avoid false positives ([`24913bf`](https://github.com/xpdustry/nohorny/commit/24913bfefbf3004258c4e084ab2771322ec2e20e))
- Lower the minimum draw instruction count for processors ([`c2db1b5`](https://github.com/xpdustry/nohorny/commit/c2db1b5bef50ffd1940a66e63d34adebf3cc88c2))
- Fix methanol not being shaded correctly ([`de808af`](https://github.com/xpdustry/nohorny/commit/de808af8d22ff3aa70087c34c1d928156e9764fc))

## v4.0.0-beta.4 - 2026-05-10

### Changes & New features

**:warning: BREAKING :warning:**

- If your plugin depends on nohorny, this release is not compatible with beta 3.
  
  - `ClassificationResponse` and `ClassificationEvent` have been changed to be more practical.
  
- Major improvements for the ViT classifier ([`5ac7b1c`](https://github.com/xpdustry/nohorny/commit/5ac7b1c7eda9a04bb07d710567bd75521647b0c7))
  
  - Added support for loading local models, alongside downloadable Hugging Face models.
  - Read the default config file for more info.
  - Reworked the server classifier configuration layout and internals.
  
- Rebuild virtual building indexes on map load ([`dd8c11b`](https://github.com/xpdustry/nohorny/commit/dd8c11b388705d2a2dafae99da5b407cf97011f8))
  
- Added basic and bearer auth to the nohorny client ([`754a3d9`](https://github.com/xpdustry/nohorny/commit/754a3d911637560ad0f0cbd88c04ede58e4df707))
  
- Include confidence score in `ClassificationResponse` ([`e8a0b09`](https://github.com/xpdustry/nohorny/commit/e8a0b096dbeed3ece0d0d882a090b42313067a38))
  
- Added a basic discord webhook utility ([`aacc753`](https://github.com/xpdustry/nohorny/commit/aacc7537e9084279296a8c971a1de3255ee69219))
  
  - Reports WARN and NSFW classifications with a rendered image, confidence score, and trace id.
  

### Bugfixes

- Added rate limits to sight-engine ([`ff9492e`](https://github.com/xpdustry/nohorny/commit/ff9492ea9eea1ecb47afa485a2238a9377e6e5c9))
  
- Make `NoHornyPreconditions#positive` actually positive ([`c8d4715`](https://github.com/xpdustry/nohorny/commit/c8d47154e9e3b5682fa75f2db6660ac823b53039))
  
- Cap size of rendered images to avoid DOS and OOMs ([`b4a90ea`](https://github.com/xpdustry/nohorny/commit/b4a90eaed92c4eec99a8a0f2ffcb2398a03a364a))
  
- Fixed stale links in `GroupingVirtualBuildingIndex` ([`20b8f6b`](https://github.com/xpdustry/nohorny/commit/20b8f6b6c789ee98ce6654e6e894f11000b7335d))
  
- Fixed ghost buildings due to server side map modifications not being tracked ([`6aa2018`](https://github.com/xpdustry/nohorny/commit/6aa201872f1a9f545c86f724c34f55b994ca8dc3)).
  
  - Thank kuko from esco for making this possible.
  
- Report the correct number of scanned buildings in AutoModerator auto delete ([`6118f09`](https://github.com/xpdustry/nohorny/commit/6118f093e93198fdd20e730202bdde6c706bbe48))
  
- Fixed NPE in `DebugHelper` ([`42ee859`](https://github.com/xpdustry/nohorny/commit/42ee8590b14181497c0dbb6db1584ff45a9c7290))
  

### Maintenance

- Ditch retries in nohorny client ([`fee1a77`](https://github.com/xpdustry/nohorny/commit/fee1a776142693875323013b5ac5285ad2d054fe))
- Improved the README
- Do not pretty print the refunded items stack in `AutoModerator` ([`e6a17e7`](https://github.com/xpdustry/nohorny/commit/e6a17e7aed0580171a31adae5aa666c87f865104))
- Clearer classification tracing in `NoHornyClient` ([`8b660d4`](https://github.com/xpdustry/nohorny/commit/8b660d489ac40e1667217c88fe8154459a59aab9))
- Properly wire methanol ([`60bed17`](https://github.com/xpdustry/nohorny/commit/60bed17fef4fb929a67485b668b4e93d83ad3b32))

## v4.0.0-beta.3 - 2026-04-22

### Changes & New features

- Implemented an iterative grouping algorithm (#71) ([`5fb96e8`](https://github.com/xpdustry/nohorny/commit/5fb96e861f565331666e561da55990b352aa7e67))
  
  - Allowing much larger groups, to be processed across several ticks.
  - Therefore, confusing less the classification models.
  
- Added status check if the nohorny server endpoint is updated ([`cc8df5d`](https://github.com/xpdustry/nohorny/commit/cc8df5da338f5fd43f4e26eeb747601c3ddf184e))
  
- Add sight-engine image classification backend (#70) ([`403d0bb`](https://github.com/xpdustry/nohorny/commit/403d0bbf56c657d18f348e11b253402c1ccbab2e))
  
- Restrict the number of concurrent nohorny requests to one (#69) ([`f6cfa5d`](https://github.com/xpdustry/nohorny/commit/f6cfa5d7254cbbb869f20b9335f0a992ae892cc7))
  
- Include trace id in ClassificationEvent ([`ee91a3e`](https://github.com/xpdustry/nohorny/commit/ee91a3e183cec0ba5337a684c0d4064d114294b4))
  

### Bugfixes

- Fix startup crash when using the ViT classifier in the norhorny server ([`8a2ce74`](https://github.com/xpdustry/nohorny/commit/8a2ce7481aafa21b60e0448da22ca935e2760465))
- Added validation for malformed nohorny server responses in client ([`81aae77`](https://github.com/xpdustry/nohorny/commit/81aae7717dc917b43134d1af35ad1ac2d2c5de98))
- Do not retry requests if the nohorny server had an internal error ([`9645e8b`](https://github.com/xpdustry/nohorny/commit/9645e8b54d8ce6338e28e8d7dfca3a7a49b4f032))
- Fix GroupingVirtualBuildingIndex#remove leaving stale links ([`cb5eb1f`](https://github.com/xpdustry/nohorny/commit/cb5eb1fd90d917aa823501201a2cc5f9e0bcddab))
- Add timeouts to rest client ([`70f0383`](https://github.com/xpdustry/nohorny/commit/70f03836c954ddc5b93e4daef8f953110a6169e9))

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
