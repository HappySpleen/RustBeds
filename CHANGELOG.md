# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Added an admin `/beds admin` flow with paginated menus for browsing owners, managing saved respawn points, renaming or removing entries, and teleporting players to saved beds or anchors.
- Added primary respawn points, respawn menu timeout handling, and configurable menu timing so players can fall back to their preferred saved point automatically.
- Added in-menu sharing and management actions for saved respawn points, replacing chat-only workflows with rename, remove, share, and primary-selection actions.
- Added respawn anchor support, including registration, charge tracking, exclusive ownership checks, and cleanup when anchors are destroyed.
- Added a SQLite-backed `respawn-points.db` store for saved respawn points and anchor ownership, with migration from legacy player data and YAML registries.
- Added a bundled default `config.yml` with documented options for menu timing, world filters, anchor support, exclusive ownership, sharing, and teleport provider selection.
- Added configurable teleport provider support with a Multiverse-Core path and vanilla fallback behavior.
- Added `config-version` tracking in `config.yml` so startup and reload can detect outdated or unsupported config layouts.

### Changed
- Migrated command registration to Paper's Brigadier lifecycle and consolidated saved-respawn workflows under `/beds`, with `admin` and `reload` subcommands.
- Refactored menus and localization around generic "respawn points" so beds and respawn anchors share the same UI, messages, and persistence model.
- Increased the default saved-point limit and enforced a GUI-safe upper bound for per-player respawn point counts.
- Updated the plugin baseline to Java 21 and Paper `1.21.11`, and refreshed plugin metadata and permissions for the expanded command and admin surface.
- Centralized plugin keys, serialization helpers, and message lookups to reduce duplicated menu and storage logic.
- Collapsed the `/beds admin` and `/beds reload` permission layout under `multiplebedspawn.admin`.

### Fixed
- Fixed admin management flows to work with `OfflinePlayer` owners instead of requiring every saved-point owner to be online.
- Fixed ownership validation so exclusive beds and respawn anchors cannot be claimed when another player already owns them.
- Fixed config initialization so new `config.yml` files keep the bundled comments and older configs are rewritten from the template with comments intact.
- Fixed shared beds and anchors so receivers get the default generated name and a `Shared By` tooltip instead of inheriting the sharer's custom name.
- Fixed rename success messages so beds and respawn anchors report the correct point type instead of using a generic bed-only message.
- Fixed respawn-point cleanup and player messaging when saved beds or anchors are broken or become unusable.
- Fixed reload behavior so configuration, saved respawn data, and anchor registries are refreshed together.
- Fixed teleport fallbacks so invalid `teleport-provider` values or unavailable Multiverse installs do not break respawn teleports.

### Removed
- Removed the standalone `/renamebed`, `/removebed`, `/sharebed`, and legacy `/respawnbed` command flow in favor of the unified `/beds` menus.
- Removed older ad hoc command-map utilities and the legacy remove-menu implementation superseded by the new menu system.
