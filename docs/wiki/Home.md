<p align="center">
  <img src="https://raw.githubusercontent.com/HappySpleen/RustBeds/main/docs/branding/rustbeds-banner.png" alt="RustBeds banner" width="855">
</p>

# RustBeds

RustBeds is a Paper plugin for saved respawn points. Players can register beds and respawn anchors, choose where to respawn after death, and manage their saved points through one `/beds` menu.

RustBeds was previously published as `MultipleBedSpawn`. The current plugin keeps upgrade paths for legacy data and permissions while moving the player experience to a unified respawn-point workflow.

## Quick links

- [Installation](Installation)
- [Player Guide](Player-Guide)
- [Admin Guide](Admin-Guide)
- [Commands and Permissions](Commands-and-Permissions)
- [Configuration](Configuration)
- [Respawn Flow and Safety](Respawn-Flow-and-Safety)
- [Migration from MultipleBedSpawn](Migration-from-MultipleBedSpawn)
- [Languages](Languages)
- [Releases and Building](Releases-and-Building)
- [Branding](Branding)

## What RustBeds adds

- Saved beds and respawn anchors with primary-point selection
- A respawn menu that opens after death when saved points are available
- A `/beds` management menu for renaming, removing, sharing, and choosing primary points
- Admin menus for browsing saved points, renaming or removing them, teleporting players, and granting points
- Safe-location checks before saved respawn teleports
- Cooldowns, world filters, sharing rules, exclusive ownership, and respawn-anchor charge tracking
- SQLite persistence in `plugins/RustBeds/respawn-points.db`
- Bundled language files for `enUS`, `deDE`, `esES`, `frFR`, `ptBR`, `ruRU`, `svSE`, and `zhCH`

## Requirements

- Paper `1.21.11`
- Java `21`
- Optional: Multiverse-Core if `teleport-provider` is set to `multiverse`

## First setup

1. Download the latest release jar from the GitHub Releases page.
2. Place the jar in your server `plugins` folder.
3. Restart the server.
4. Review `plugins/RustBeds/config.yml`.
5. Give staff `rustbeds.admin` if they should use `/beds admin` or `/beds reload`.

The default config is documented inline, and the [Configuration](Configuration) page explains each setting in server-owner terms.
