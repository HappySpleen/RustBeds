# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [1.10.0] - 2026-11-22

RustBeds now targets **Paper 26.3** and requires **Java 25 or newer**. This release adds native Bedrock menus and migrates existing saved beds during the Paper upgrade.

### Added

- Native Bedrock forms for `/beds`, respawn selection after death, saved point management, renaming, share and transfer requests, and `/beds admin`. They work with Geyser-Spigot or a Geyser proxy configured to forward player data to Floodgate on the Paper server. Java players continue to use inventory and anvil menus.
- A **Respawn point details** screen for Bedrock players. Bedrock share and transfer notifications direct players to `/beds requests` to review pending requests.

### Changed

- Physical bed IDs now live in `plugins/RustBeds/respawn-points.db`. RustBeds imports legacy bed IDs as Paper converts world chunks, preserving saved names, ownership, shares, cooldowns, and primary respawn points.
- Updated menu titles, item text, tooltips, the Java rename anvil, and backup version reporting to use current Paper APIs.

### Fixed

- Fixed a Geyser and Floodgate classloader conflict that prevented Bedrock forms from opening through `/beds` or after death.
- Bedrock players stay at their sky spawn while choosing a respawn point when `spawn-on-sky` is enabled. Closing the death form sends them to their default spawn.
- Bed cleanup respects cancelled destruction events, clears pending requests for destroyed points, and prevents a replacement bed from inheriting the old bed’s registration.

### Upgrading from 1.9.0

Stop your old server and back up your worlds and `plugins/RustBeds` together. Test the upgrade on a copy, and **replace the old RustBeds JAR before the copy starts on Paper 26.3 for the first time**. If Paper already converted a chunk without RustBeds capturing its bed IDs, use a backup from before conversion to recover those registrations.

## [1.10.0-pre.2] - 2026-11-20

### Added
- Added native Bedrock forms for respawn selection, bed management, sharing and transfer requests, and admin menus. The forms work with Geyser-Spigot or a Geyser proxy forwarding player data to Floodgate on Paper.

### Fixed
- Fixed a classloader conflict that prevented Geyser and Floodgate from opening Bedrock forms for `/beds` and death respawns.
- Bedrock players stay at their sky spawn while choosing a respawn point when `spawn-on-sky` is enabled.

## [1.10.0-pre.1] - 2026-11-19

### Changed
- This test release targets Paper 26.3 (build 256) and Java 25 or newer.
- RustBeds stores physical bed UUIDs in `respawn-points.db` because beds lost their block entity data in Minecraft 26.2. It imports the old UUIDs as Paper converts chunks, preserving saved names, owners, shares, cooldowns, and primary selections.
- Updated menu titles, item text, tooltips, and the rename anvil to current Paper APIs.

### Testing an upgrade
- Back up the worlds and `plugins/RustBeds` together while the old server is stopped. Test with a separate copy and install this JAR before starting the copy on 26.3 for the first time. Remove the old RustBeds JAR from the copy's `plugins` folder.
- If a chunk has already been converted without RustBeds listening, Paper cannot supply its original bed UUID. Test from a backup taken before that conversion.
- Check an existing shared bed and primary point, a new bed, the rename prompt, death and respawn, and an anchor. Break a shared bed, check both owners' menus, and verify that a replacement at the same coordinates has to be registered again.

## [1.9.0] - 2026-05-15

### Added
- Added a Modrinth update checker that reports newer release, dev, and dev alpha builds to the console and online admins.

## [1.8.0] - 2026-05-12

### Added
- Added config-version backups under `plugins/RustBeds/backups/config`, plus an optional fresh-config-on-update mode.
- Added `respawn-points.db` backups under `plugins/RustBeds/backups/DB` for plugin-version updates and scheduled day-based retention.
- Added a red console warning when `backups.config.fresh-on-version-change` replaces `config.yml` with bundled defaults, plus yellow console logs showing created backup file locations.

## [1.7.0] - 2026-05-11

### Changed
- Changed player and admin audit logs from single-line logfmt to multi-line admin-readable entries with aligned details and trace UUIDs.
- Added audit log format controls for readable, JSON Lines, and logfmt output, plus UUID, spawn-coordinate, coordinate-decimal, and divider options.

### Fixed
- Fixed the anvil rename UI leaking its temporary name tag into the player's inventory.

## [1.6.1] - 2026-05-08

### Changed
- Trimmed default audit log lines and added `audit-log.verbose` to restore the full detail level when needed.

## [1.6.0] - 2026-05-06

### Added
- Added configurable player and admin audit logs under `plugins/RustBeds/Logs`, with default 30-day retention and tracking for rename, remove, share/give, and admin teleport actions.

## [1.5.1] - 2026-05-06

### Changed
- Changed respawn point renaming to use an anvil interface instead of chat input.

## [1.5.0] - 2026-05-06

### Added
- Added bStats metrics reporting with shaded bStats `3.2.1` support and config-state charts for anchors, sharing, exclusivity, and teleport provider.

## [1.4.4 (26.1)] - 2026-05-05

### Changed
- Updated the build baseline to Java 25 and Paper API `26.1.2`.
- Refactored respawn point menu internals to share menu item builders, status models, lore formatting, offline-player sorting, and rename prompt handling without changing player-facing behavior.

## [1.4.4] - 2026-05-01

### Changed
- Changed the default safe-location vertical search radius to `0.5` blocks and required collision-free height to `1.5` blocks.

## [1.4.3] - 2026-05-01

### Changed
- Sorted player-list menus with online players first, then player names A to Z.

## [1.4.2] - 2026-05-01

### Added
- Added a clickable pending-request chat link that opens the share or transfer requests page directly with `/beds requests`.

## [1.4.1] - 2026-05-01

### Changed
- Changed player sharing UI text to say transfer or transferred when `exclusive-bed: true` makes requests transfer ownership.

## [1.4.0] - 2026-05-01

### Added
- Added `safe-location-search` config tuning for obstruction search radius and required collision-free respawn height.

## [1.3.0] - 2026-05-01

### Added
- Added pending player share invites with menu-driven accept and deny actions, expiry, and queued offline delivery for known offline recipients.

## [1.2.0] - 2026-05-01

### Added
- Added safe-location validation before saved respawn point teleports, with obstructed points blocked until a nearby 1.625-block-tall non-colliding space is available within 2 blocks horizontally and 0.375 blocks vertically.

## [1.1.4] - 2026-05-01

### Added
- Added temporary darkness and blindness effects while the death respawn menu is open so the background view is as dark as possible.

## [1.1.3] - 2026-05-01

### Fixed
- Fixed the `/beds admin` selected respawn point action menu so it shows the player's saved bed name, such as `Home`, instead of only a generic admin action title.

## [1.1.2] - 2026-04-29

### Fixed
- Fixed admin **Give point** exclusivity so `exclusive-bed: true` transfers the respawn point away from the selected owner, while `exclusive-bed: false` keeps the original owner as a copy.

## [1.1.1] - 2026-04-29

### Fixed
- Fixed the admin **Give point** action so admins can grant respawn points to known offline players, with offline recipient names shown in red and the received message queued for their next login.

## [1.1.0] - 2026-04-29

### Added
- Added an admin **Give point** action that lets admins grant a saved respawn point to another online player without adding `Shared By` provenance.

### Fixed
- Stopped migrated legacy configs from carrying forward the removed `disable-sleeping` and `remove-beds-gui` options.

## [1.0.0] - 2026-04-29

### Added
- Added the core saved-bed respawn workflow, including bed registration, cooldowns, world filters, linked-world browsing, and optional sky-spawn handling.
- Added a unified `/beds` management flow with rename, remove, share, and primary-point actions for saved respawn points.
- Added admin `/beds admin` menus for browsing owners, managing saved respawn points, and teleporting players to saved beds or anchors.
- Added respawn anchor support with charge tracking, exclusive ownership checks, and cleanup when anchors are destroyed or depleted.
- Added a SQLite-backed `respawn-points.db` store, pending offline notifications, and migration paths from legacy player PDC data plus YAML ownership and anchor registries.
- Added a bundled, comment-preserving `config.yml` with respawn timing, world filters, anchor support, sharing, exclusivity, and teleport-provider options.
- Added configurable teleport-provider support with a Multiverse-Core path and vanilla fallback behavior.
- Added bundled translations for `deDE`, `esES`, `frFR`, `ptBR`, `ruRU`, `svSE`, and `zhCH` alongside `enUS`.

### Changed
- Renamed the project from `MultipleBedSpawn` to `RustBeds`, moved the Java namespace to `me.happy.rustbeds`, and started Semantic Versioning at `1.0.0`.
- Updated the build baseline to Java 21 and Paper `1.21.11`, and migrated command registration to Paper's Brigadier lifecycle.
- Consolidated player workflows under `/beds`, with `admin` and `reload` subcommands replacing older standalone command roots.
- Refactored menus, messaging, and persistence around generic "respawn points" so beds and respawn anchors share the same UI and storage model.
- Switched the plugin data folder to `plugins/RustBeds` and added automatic migration for legacy `config.yml`, `respawn-points.db`, and remaining migration helper files from `plugins/MultipleBedSpawn`.
- Updated the primary permission nodes to `rustbeds.*` while keeping legacy `multiplebedspawn.*` aliases working for existing server setups.
- Preserved stable data keys and serialized-model compatibility so existing saved respawn points continue to load after the rename.

### Fixed
- Fixed ownership validation so exclusive beds and respawn anchors cannot be claimed when another player already owns them.
- Fixed sharing behavior so receivers get default generated names, `Shared By` provenance, and correct copy-vs-transfer semantics.
- Fixed admin management flows to work with `OfflinePlayer` owners instead of requiring every saved-point owner to be online.
- Fixed respawn timeout fallbacks, stale-point cleanup, and destroyed-point notifications for invalid beds and anchors.
- Fixed config initialization so new `config.yml` files keep the bundled comments and older configs are rewritten from the template with comments intact.
- Fixed rename success messages so beds and respawn anchors report the correct point type instead of using a generic bed-only message.
- Fixed teleport fallbacks so invalid `teleport-provider` values or unavailable Multiverse installs do not break respawn teleports.
- Fixed earlier compatibility issues across Spigot/Paper respawn handling, inventory sizing, join flows, invisibility restoration, and end-portal respawns.

### Removed
- Removed the standalone `/renamebed`, `/removebed`, `/sharebed`, and legacy `/respawnbed` command flow in favor of the unified `/beds` menus.
- Removed older ad hoc command-map utilities and superseded menu implementations that were replaced by the current inventory-driven workflow.
