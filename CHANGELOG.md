# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
